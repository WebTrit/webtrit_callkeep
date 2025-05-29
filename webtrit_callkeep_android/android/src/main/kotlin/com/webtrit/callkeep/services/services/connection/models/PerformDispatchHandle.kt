package com.webtrit.callkeep.services.services.connection.models

import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.services.broadcaster.ConnectionPerform

typealias PerformDispatchHandle = (ConnectionPerform, data: CallMetadata?) -> Unit
