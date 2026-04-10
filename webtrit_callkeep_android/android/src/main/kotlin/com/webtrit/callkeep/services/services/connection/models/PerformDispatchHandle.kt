package com.webtrit.callkeep.services.services.connection.models

import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.services.broadcaster.ConnectionEvent

typealias PerformDispatchHandle = (ConnectionEvent, data: CallMetadata?) -> Unit
