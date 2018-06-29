package io.ktor.experimental.letsencrypt

import io.ktor.application.*
import io.ktor.client.engine.*
import io.ktor.client.engine.apache.*
import io.ktor.network.tls.certificates.*
import io.ktor.pipeline.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.*
import org.shredzone.acme4j.*
import org.shredzone.acme4j.challenge.*
import org.shredzone.acme4j.util.*
import org.slf4j.*
import java.io.*
import java.security.*
import java.security.cert.*
import java.util.logging.*
import javax.net.ssl.*
import kotlin.collections.set


class LetsEncrypt(val config: Configuration) {
    data class DomainSet(val config: Configuration, val domains: List<String>, val organization: String) {
        val mainDomain = domains.first()
    }

    class Configuration {
        //var acmeEndPoint = "https://acme-v01.api.letsencrypt.org/directory"
        //var acmeDirectoryEndPoint = "https://acme-staging.api.letsencrypt.org/directory"
        internal var kind = "staging"
        internal var acmeDirectoryEndPoint = "https://acme-staging-v02.api.letsencrypt.org/directory"
        //val acmeDirectoryEndPoint = "acme://letsencrypt.org/staging"
        var engine: HttpClientEngine = Apache.create { }
        var certFolder = File(".")
        var certsize = 4096
        lateinit var email: String
        internal val domains = arrayListOf<DomainSet>()

        fun setProduction() {
            kind = "production"
            acmeDirectoryEndPoint = "https://acme-v02.api.letsencrypt.org/directory"
        }

        fun setStaging() {
            kind = "staging"
            acmeDirectoryEndPoint = "https://acme-staging-v02.api.letsencrypt.org/directory"
        }

        /**
         * A domain that will require HTTPS. Ktor must be processing HTTP calls for this domain already.
         */
        fun addDomainSet(mainDomain: String, vararg extraDomains: String, organization: String = "myorganization") {
            domains += DomainSet(this, listOf(mainDomain) + extraDomains, organization)
        }
    }

    companion object Feature : ApplicationFeature<Application, Configuration, LetsEncrypt> {
        override val key = AttributeKey<LetsEncrypt>(LetsEncrypt::class.simpleName!!)
        override fun install(pipeline: Application, configure: Configuration.() -> Unit): LetsEncrypt {
            val feature = LetsEncrypt(
                Configuration().apply(configure)
            )
            pipeline.intercept(ApplicationCallPipeline.Infrastructure) { feature.intercept(this) }
            pipeline.environment.monitor.subscribe(ApplicationStarted) {
                launch(newSingleThreadContext("ConfigureLetsEncrypt")) {
                    feature.applicationStarted()
                }
            }
            return feature
        }
    }

    private fun intercept(context: PipelineContext<Unit, ApplicationCall>) {
        context.application.routing {
            // https://ietf-wg-acme.github.io/acme/draft-ietf-acme-acme.html#http-challenge
            get("/.well-known/acme-challenge/{token}") {
                val host = call.request.host() ?: error("No host!")
                val token = call.parameters["token"]
                val content = tokens[host]?.get(token) ?: "error"
                call.respondText(content)
            }
        }
    }

    val tokens = LinkedHashMap<String, LinkedHashMap<String, String>>()

    val DomainSet.crtFile get() = File(config.certFolder, "letsencrypt-${config.kind}-chain-${mainDomain}.crt")
    val DomainSet.csrFile get() = File(config.certFolder, "letsencrypt-${config.kind}-domain-${mainDomain}.csr")
    val DomainSet.keyFile get() = File(config.certFolder, "letsencrypt-${config.kind}-key-${mainDomain}.key")

    val userCertFile = File(config.certFolder, "letsencrypt-${config.kind}-user-${File(config.email).name}.cert")

    val session = Session(config.acmeDirectoryEndPoint)

    private val account by lazy {
        val email = config.email

        if (!userCertFile.exists()) {
            val keyPair = generateKeyPair(config.certsize)
            userCertFile.writeBytes(keyPair.toByteArray())
        }

        val keyPair = KeyPair(userCertFile.readBytes())

        val login = AccountBuilder()
            .addContact("mailto:$email")
            .agreeToTermsOfService()
            .useKeyPair(keyPair)
            .createLogin(session)

        login.account
    }

    val logger = LoggerFactory.getLogger("LetsEncrypt")

    fun applicationStarted() {
        for (domainSet in config.domains) {
            logger.trace("Processing $domainSet")
            val order = account.newOrder().domains(domainSet.domains).create()
            val crtFile = domainSet.crtFile
            val csrFile = domainSet.csrFile

            logger.trace("${order.authorizations}")

            for (auth in order.authorizations) {
                logger.trace("${auth.status}")
                if (auth.status != Status.VALID) {
                    val challenge =
                        auth.findChallenge<Http01Challenge>(Http01Challenge.TYPE) ?: error("Can't find http challenge")

                    logger.trace("$challenge")
                    logger.trace("${challenge.location}")
                    logger.trace("${challenge.validated}")
                    logger.trace(challenge.authorization)
                    logger.trace(challenge.token)
                    logger.trace(auth.domain)

                    val domainMap = tokens.getOrPut(auth.domain) { LinkedHashMap() }
                    domainMap[challenge.token] = challenge.authorization

                    challenge.trigger()

                    logger.trace("auth.status: ${auth.status}")
                    var count = 0
                    while (auth.status != Status.VALID) {
                        logger.trace("auth.status: ${auth.status}")
                        Thread.sleep(6000L)
                        auth.update()
                        count++
                        if (auth.status == Status.INVALID) error("Invalid auth")
                        if (count >= 10) error("Couldn't process")
                    }

                    val domainKeyPair = generateKeyPair(config.certsize)

                    domainSet.keyFile.writeBytes(domainKeyPair.toByteArray())


                    logger.trace("Creating $csrFile...")

                    val csrb = CSRBuilder()
                    for (domain in domainSet.domains) {
                        csrb.addDomain(domain)
                    }
                    csrb.setOrganization(domainSet.organization)
                    csrb.sign(domainKeyPair)
                    val csr = csrb.encoded

                    csrb.write(FileWriter(csrFile))

                    order.execute(csr)

                    logger.trace("CERT.order.status: ${order.status}")
                    while (order.status != Status.VALID) {
                        logger.trace("CERT.order.status: ${order.status}")
                        Thread.sleep(3000L)
                        order.update()
                    }

                    val cert = order.certificate ?: error("Can't download certificate chain!")
                    FileWriter(crtFile).use { cert.writeCertificate(it) }
                }
            }

            val domainSet = config.domains.first()

            val cert = loadPublicX509(domainSet.crtFile.readBytes())
            //val privateKey = loadPrivateKey(domainSet.csrFile.readBytes())
            val privateKey = KeyPair(domainSet.keyFile.readBytes()).private

            val keyStore = LetsEncryptCerts.keyStore

            keyStore.setEntry(
                LetsEncryptCerts.alias,
                KeyStore.PrivateKeyEntry(privateKey, arrayOf(cert)),
                KeyStore.PasswordProtection(
                    charArrayOf()
                )
            )

            val algo = Security.getProperty("ssl.KeyManagerFactory.algorithm") ?: "SunX509"
            val kmf = KeyManagerFactory.getInstance(algo)
            kmf.init(keyStore, charArrayOf())


            val ctx = SSLContext.getInstance("TLS")
            ctx.init(kmf.keyManagers, null, null)
        }
    }


}

object LetsEncryptCerts {
    val alias = "ktor-alias"
    val keyStore =
        generateCertificate(File("temp.keystore", alias), keyAlias = alias, keyPassword = "", jksPassword = "")
}

fun generateKeyPair(certsize: Int) =
    KeyPairGenerator.getInstance("RSA").apply { initialize(certsize, SecureRandom()) }.generateKeyPair()

fun KeyPair(bytes: ByteArray): KeyPair =
    StringReader(bytes.toString(Charsets.UTF_8)).use { KeyPairUtils.readKeyPair(it) }

fun KeyPair.toByteArray(): ByteArray =
    CharArrayWriter().apply { KeyPairUtils.writeKeyPair(this@toByteArray, this) }.toString().toByteArray(Charsets.UTF_8)

fun loadPublicX509(bytes: ByteArray): X509Certificate? =
    CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(bytes)) as X509Certificate
