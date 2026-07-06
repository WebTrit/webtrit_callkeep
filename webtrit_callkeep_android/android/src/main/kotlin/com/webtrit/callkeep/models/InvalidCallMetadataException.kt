package com.webtrit.callkeep.models

/**
 * Thrown when call metadata is missing a field required to proceed -- e.g. an outgoing
 * call without a destination number, or a connection broadcast that arrives without a
 * handle.
 *
 * Surfaced through the normal failure channels (dispatch error / failed-call store)
 * rather than crashing the service or leaving a half-established "ghost" call.
 */
class InvalidCallMetadataException(
    message: String,
) : Exception(message)
