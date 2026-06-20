package io.github.daisycatts.punishbridge

private val usernamePattern: Regex = Regex("^[A-Za-z0-9_]{1,16}$")
private val serverNamePattern: Regex = Regex("^[A-Za-z0-9._-]{1,64}$")

public object RequestValidator {
    @JvmStatic
    public fun validate(request: PunishmentRequest): String? {
        validateTarget(request.target)?.let { return it }
        validateActor(request.actor)?.let { return it }
        validateScope(request.scope)?.let { return it }
        validateReason(request.reason, required = true)?.let { return it }

        if (request.duration is PunishmentDuration.Temporary && !request.duration.duration.isPositive) {
            return "Temporary punishment duration must be positive"
        }
        if (request.kind == PunishmentKind.KICK && request.duration != PunishmentDuration.Permanent) {
            return "Kicks cannot have a duration"
        }
        return null
    }

    @JvmStatic
    public fun validate(request: RevocationRequest): String? {
        request.reason?.let { validateReason(it, required = false)?.let { error -> return error } }
        validateActor(request.actor)?.let { return it }
        return when (val selector = request.selector) {
            is RevocationSelector.ByReference -> {
                if (selector.reference.providerId.isBlank() || selector.reference.externalId.isBlank()) {
                    "Punishment references require provider and external IDs"
                } else {
                    null
                }
            }
            is RevocationSelector.ByTarget -> validateTarget(selector.target) ?: validateScope(selector.scope)
        }
    }

    @JvmStatic
    public fun validate(query: PunishmentQuery): String? =
        validateTarget(query.target)
            ?: validateScope(query.scope)
            ?: if (query.kinds.isEmpty()) "At least one punishment kind is required" else null

    private fun validateTarget(target: PunishmentTarget): String? =
        when (target) {
            is PunishmentTarget.Player -> validateUsername(target.username)
            is PunishmentTarget.Address -> null
            is PunishmentTarget.PlayerAndAddress -> validateUsername(target.username)
        }

    private fun validateActor(actor: PunishmentActor): String? =
        when (actor) {
            PunishmentActor.Console -> null
            is PunishmentActor.Player -> validateUsername(actor.username)
            is PunishmentActor.System -> {
                if (actor.componentName.isBlank() || actor.componentName.length > 64) {
                    "System component name must contain 1 to 64 characters"
                } else {
                    validateControlCharacters(actor.componentName, "System component name")
                }
            }
        }

    private fun validateUsername(username: String): String? =
        if (usernamePattern.matches(username)) null else "Invalid Minecraft username: $username"

    private fun validateScope(scope: PunishmentScope): String? =
        if (scope is PunishmentScope.NamedServer && !serverNamePattern.matches(scope.name)) {
            "Invalid server name: ${scope.name}"
        } else {
            null
        }

    private fun validateReason(
        reason: String,
        required: Boolean,
    ): String? {
        if (required && reason.isBlank()) return "Reason must not be blank"
        if (reason.length > 255) return "Reason must not exceed 255 characters"
        return validateControlCharacters(reason, "Reason")
    }

    private fun validateControlCharacters(
        value: String,
        field: String,
    ): String? =
        if (value.any { it == '\r' || it == '\n' || it.code < 0x20 && it != '\t' }) {
            "$field contains command-control characters"
        } else {
            null
        }
}
