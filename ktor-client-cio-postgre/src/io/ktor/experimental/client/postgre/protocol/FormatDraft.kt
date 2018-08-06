package io.ktor.experimental.client.postgre.protocol

import kotlinx.io.core.*

/**
 * According to:
 * https://www.postgresql.org/docs/current/static/protocol-message-formats.html
 *
 * note that length is always includes itself
 */

/**
 * still missing:
 *
 * BindComplete (B)
 * CancelRequest (F)
 * SslRequest
 * StartupMessage (F)
 */

/**
 * TODO: clash
 */
internal enum class MessageType(val code: Char) {
    AUTHENTICATION_REQUEST('R'),
    CANCELLATION_KEY('K'),
    BIND('B'),
    CLOSE('C'),

    CLOSE_COMPLETE('3'),
    COMMAND_COMPLETE('C'),

    COPY_DATA('d'),
    COPY_DONE('c'),
    COPY_FAIL('f'),
    COPY_IN_RESPONSE('G'),
    COPY_OUT_RESPONSE('H'),
    COPY_BOTH_RESPONSE('W'),

    DATA_ROW('D'),
    DESCRIBE('D'),
    EMPTY_QUERY_RESPONSE('I'),
    ERROR_RESPONSE('E'),
    EXECUTE('E'),
    FLUSH('H'),
    FUNCTION_CALL('F'),
    FUNCTION_CALL_RESPONSE('V'),

    GSS_RESPONSE('p'),
    NEGOTIATE_PROTOCOL_VERSION('v'),
    NO_DATA('n'),

    NOTICE_RESPONSE('N'),
    NOTIFICATION_RESPONSE('A'),

    PARAMETER_DESCRIPTION('t'),
    PARAMETER_STATUS('S'),

    PARSE('P'),
    PARSE_COMPLETE('1'),
    PASSWORD_MESSAGE('p'),

    PORTAL_SUSPENDED('s'),

    QUERY('Q'),
    READY_FOR_QUERY('Z'),
    ROW_DESCRIPTION('T'),

    SASL_INITIAL_RESPONSE('p'),
    SASL_RESPONSE('p'),

    SYNC('S'),
    TERMINATE('X')
}

internal enum class AuthenticationType(val code: Int) {
    OK(0),
    KERBEROS_V5(2),
    CLEARTEXT_PASSWORD(3),
    MD5_PASSWORD(5),
    SCM_CREDENTIAL(6),
    GSS(7),
    SSPI(9),

    /**
     * note that length of the following messages is before the [code]
     */
    GSS_CONTINUE(8),
    SASL(10),

    SASL_CONTINUE(11),
    SASL_FINAL(12)
}

