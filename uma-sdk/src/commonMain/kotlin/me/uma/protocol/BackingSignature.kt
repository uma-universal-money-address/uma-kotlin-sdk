package me.uma.protocol

import kotlinx.serialization.Serializable

/**
 * A signature by a backing VASP that can attest to the authenticity of the message, along with its associated domain.
 */
@Serializable
data class BackingSignature(
  /**
   * Domain is the domain of the VASP that produced the signature. Public keys for this VASP will be fetched from the
   * domain at /.well-known/lnurlpubkey and used to verify the signature.
   */
  val domain: String,
  /** Signature is the signature of the payload. */
  val signature: String,
)
