/**
 * Generated error codes - DO NOT MODIFY MANUALLY
 */

package me.uma.generated

/**
 * Error codes used throughout the UMA SDK
 */
enum class ErrorCode(val httpStatusCode: Int) {
    /**
     * Error fetching counterparty public key for validating signatures or encrypting messages
     */
    COUNTERPARTY_PUBKEY_FETCH_ERROR(424),

    /**
     * Error parsing the counterparty public key response
     */
    INVALID_PUBKEY_FORMAT(400),

    /**
     * The provided certificate chain is invalid
     */
    CERT_CHAIN_INVALID(400),

    /**
     * The provided certificate chain has expired
     */
    CERT_CHAIN_EXPIRED(400),

    /**
     * The provided signature is not valid
     */
    INVALID_SIGNATURE(401),

    /**
     * The provided timestamp is not valid
     */
    INVALID_TIMESTAMP(400),

    /**
     * The provided nonce is not valid
     */
    INVALID_NONCE(400),

    /**
     * An unexpected error occurred on the server
     */
    INTERNAL_ERROR(500),

    /**
     * This party does not support non-UMA LNURLs
     */
    NON_UMA_LNURL_NOT_SUPPORTED(403),

    /**
     * Missing required UMA parameters
     */
    MISSING_REQUIRED_UMA_PARAMETERS(400),

    /**
     * The counterparty UMA version is not supported
     */
    UNSUPPORTED_UMA_VERSION(412),

    /**
     * Error parsing the LNURLP request
     */
    PARSE_LNURLP_REQUEST_ERROR(400),

    /**
     * This user has exceeded the velocity limit and is unable to receive payments at this time
     */
    VELOCITY_LIMIT_EXCEEDED(403),

    /**
     * The user for this UMA was not found
     */
    USER_NOT_FOUND(404),

    /**
     * The user for this UMA is not ready to receive payments at this time
     */
    USER_NOT_READY(403),

    /**
     * The request corresponding to this callback URL was not found
     */
    REQUEST_NOT_FOUND(404),

    /**
     * Error parsing the payreq request
     */
    PARSE_PAYREQ_REQUEST_ERROR(400),

    /**
     * The amount provided is not within the min/max range
     */
    AMOUNT_OUT_OF_RANGE(400),

    /**
     * The currency provided is not valid or supported
     */
    INVALID_CURRENCY(400),

    /**
     * Payments from this sender are not accepted
     */
    SENDER_NOT_ACCEPTED(400),

    /**
     * Payer data is missing fields that are required by the receiver
     */
    MISSING_MANDATORY_PAYER_DATA(400),

    /**
     * Receiver does not recognize the mandatory payee data key
     */
    UNRECOGNIZED_MANDATORY_PAYEE_DATA_KEY(501),

    /**
     * Error parsing the utxo callback
     */
    PARSE_UTXO_CALLBACK_ERROR(400),

    /**
     * This party does not accept payments with the counterparty
     */
    COUNTERPARTY_NOT_ALLOWED(403),

    /**
     * Error parsing the LNURLP response
     */
    PARSE_LNURLP_RESPONSE_ERROR(400),

    /**
     * Error parsing the payreq response
     */
    PARSE_PAYREQ_RESPONSE_ERROR(400),

    /**
     * The LNURLP request failed
     */
    LNURLP_REQUEST_FAILED(424),

    /**
     * The payreq request failed
     */
    PAYREQ_REQUEST_FAILED(424),

    /**
     * No compatible UMA protocol version found between sender and receiver
     */
    NO_COMPATIBLE_UMA_VERSION(424),

    /**
     * The provided invoice is invalid
     */
    INVALID_INVOICE(400),

    /**
     * The invoice has expired
     */
    INVOICE_EXPIRED(400),

    /**
     * The quote has expired
     */
    QUOTE_EXPIRED(400),

    /**
     * The provided input is invalid
     */
    INVALID_INPUT(400),

    /**
     * The request format is invalid
     */
    INVALID_REQUEST_FORMAT(400),

    /**
     * This action is not permitted for this user
     */
    FORBIDDEN(403),

    /**
     * This functionality is not implemented
     */
    NOT_IMPLEMENTED(501),

    /**
     * The requested quote was not found
     */
    QUOTE_NOT_FOUND(404),
}
