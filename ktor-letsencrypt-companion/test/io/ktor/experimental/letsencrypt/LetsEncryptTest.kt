package io.ktor.experimental.letsencrypt

import io.ktor.http.*

// https://github.com/shred/acme4j
// https://github.com/noteed/acme
class LetsEncryptTest {
    /*
    val httpMockEngine = MockEngine { call ->
        when (url.fullUrl) {
            "https://acme-staging.api.letsencrypt.org/directory" -> {
                //https://acme-staging.api.letsencrypt.org/directory
                MockHttpResponse(
                    // https://letsencrypt.org/docs/acme-protocol-updates/
                    call, HttpStatusCode.OK, ByteReadChannel(
                        """{
                    "key-change": "https://acme-staging.api.letsencrypt.org/acme/key-change",
                    "meta": {
                        "caaIdentities": [
                            "letsencrypt.org"
                        ],
                        "terms-of-service": "https://letsencrypt.org/documents/LE-SA-v1.2-November-15-2017.pdf",
                        "website": "https://letsencrypt.org/docs/staging-environment/"
                    },
                    "new-authz": "https://acme-staging.api.letsencrypt.org/acme/new-authz",
                    "new-cert": "https://acme-staging.api.letsencrypt.org/acme/new-cert",
                    "new-reg": "https://acme-staging.api.letsencrypt.org/acme/new-reg",
                    "revoke-cert": "https://acme-staging.api.letsencrypt.org/acme/revoke-cert",
                    "vo2enpjSs8o": "https://community.letsencrypt.org/t/adding-random-entries-to-the-directory/33417"
                }
                """.trimIndent().toByteArray()
                    ),
                    headersOf(
                        "Content-Type" to listOf(ContentType.Application.Json.toString())
                    )
                )
            }
            else -> error("Unhandled ${url.fullUrl}")
        }
    }

    @Test
    fun name() {
        withTestApplication {
            runBlocking {
                val letsEncrypt = application.install(LetsEncrypt) {
                    engine = httpMockEngine
                    email = "soywiz@gmail.com"
                    addDomainSet("example.org")
                }
                letsEncrypt.applicationStarted()
            }
        }
    }
    */
}

private val Url.hostWithPortIfRequired: String get() = if (port == protocol.defaultPort) host else hostWithPort
private val Url.fullUrl: String get() = "${protocol.name}://$hostWithPortIfRequired$fullPath"
