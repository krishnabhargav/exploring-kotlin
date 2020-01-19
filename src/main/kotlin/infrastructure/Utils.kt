package infrastructure

import com.capsule.atlas.WriteResult
import com.capsule.atlas.models.Result
import com.capsule.atlas.models.getOrThrow
import com.capsule.atlas.models.map


fun Result<WriteResult, Exception>.asRest(): WriteResult.Rest {
    return this.map {
        when (val wr = it) {
            is WriteResult.Rest -> wr
            else -> throw Exception("Not a rest result")
        }
    }.getOrThrow()
}