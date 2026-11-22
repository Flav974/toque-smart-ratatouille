package app.ratatouille.ia

import app.ratatouille.core.ia.RecetteIa
import app.toque.core.i18n.tr
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.BadRequestException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import java.time.Duration
import kotlin.jvm.optionals.getOrNull

/**
 * Appel à l'API Claude pour « Cuisiner avec l'IA » (EX-71, EX-72) : Claude Haiku 5.5, consigne
 * fixe, sortie structurée au schéma [RecetteIa.SCHEMA]. Renvoie le texte JSON brut ; la
 * vérification (format et sécurité) est faite ensuite par [RecetteIa.versRecette].
 *
 * Bloquant : à appeler hors du fil principal. La clé n'est jamais journalisée ni affichée.
 */
class ClaudeRecette(cle: String) {

    /** Échec avec un message simple pour l'écran (EX-77). */
    class Echec(message: String) : Exception(message)

    private val client = AnthropicOkHttpClient.builder()
        .apiKey(cle)
        .timeout(Duration.ofSeconds(120))
        .build()

    private val format = JsonOutputFormat.builder()
        .schema(JsonOutputFormat.Schema.builder().apply {
            for ((k, v) in RecetteIa.SCHEMA) putAdditionalProperty(k, JsonValue.from(v))
        }.build())
        .build()

    fun genere(consigne: String, demande: String): String {
        val params = MessageCreateParams.builder()
            .model(MODELE)
            .maxTokens(16_000L)
            .system(consigne)
            .addUserMessage(demande)
            .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.MEDIUM).format(format).build())
            .build()
        val reponse = try {
            client.messages().create(params)
        } catch (e: UnauthorizedException) {
            throw Echec(tr("Clé API refusée. Vérifiez-la dans Réglages › IA."))
        } catch (e: PermissionDeniedException) {
            throw Echec(tr("Clé API sans droit d'accès à ce modèle."))
        } catch (e: RateLimitException) {
            throw Echec(tr("Trop de demandes ou plafond de dépense atteint. Réessayez plus tard."))
        } catch (e: BadRequestException) {
            throw Echec(tr("Demande refusée par l'API (%d).", e.statusCode()))
        } catch (e: AnthropicServiceException) {
            throw Echec(tr("Le service Claude ne répond pas correctement (%d). Réessayez plus tard.", e.statusCode()))
        } catch (e: AnthropicIoException) {
            throw Echec(tr("Pas de connexion à Internet, ou le service Claude est injoignable."))
        }
        when (reponse.stopReason().getOrNull()) {
            StopReason.REFUSAL -> throw Echec(tr("Claude a refusé cette demande. Essayez une autre formulation."))
            StopReason.MAX_TOKENS -> throw Echec(tr("Réponse trop longue, coupée. Demandez une recette plus simple."))
            else -> {}
        }
        val texte = reponse.content().mapNotNull { it.text().getOrNull()?.text() }.joinToString("")
        if (texte.isBlank()) throw Echec(tr("Réponse vide de Claude."))
        return texte
    }

    /** La fermeture touche au réseau : jamais sur le fil principal. */
    fun ferme() {
        Thread({ runCatching { client.close() } }, "fermeture-claude").start()
    }

    companion object {
        const val MODELE = "claude-haiku-5-5"
    }
}
