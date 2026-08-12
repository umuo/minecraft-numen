package com.dwinovo.numen.agent.llm;

import java.util.Base64;

/**
 * One transient image supplied by the owner to a multimodal model.
 *
 * <p>The bytes deliberately live only in memory. {@link ConvoLog} persists the
 * accompanying user text but never serialises this payload, so a pasted image
 * cannot silently turn a small JSONL transcript into a multi-megabyte file.
 */
public record InputImage(String mediaType, byte[] bytes) {

    public InputImage {
        mediaType = mediaType == null || mediaType.isBlank() ? "image/png" : mediaType;
        if (!mediaType.startsWith("image/")) {
            throw new IllegalArgumentException("not an image media type: " + mediaType);
        }
        bytes = bytes == null ? new byte[0] : bytes.clone();
        if (bytes.length == 0) {
            throw new IllegalArgumentException("image is empty");
        }
    }

    /** Record accessors normally expose an array directly; keep this DTO immutable. */
    @Override
    public byte[] bytes() {
        return bytes.clone();
    }

    /** Provider adapters need the encoded body but should not each reinvent it. */
    public String base64() {
        return Base64.getEncoder().encodeToString(bytes);
    }
}
