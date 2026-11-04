package hr.bertlab.fluteassistant.ui.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import java.io.File

/**
 * Turns an animal [emoji] round to face right, the way the metronome's ring goes faster; every
 * speed animal (the ring's, the flute's and the notes summary's) does. Which way an emoji faces
 * is up to the phone's emoji set, so an animal is mirrored only where that set draws it facing
 * left (see [mirrorsToFaceRight]).
 */
fun Modifier.facingRight(emoji: String): Modifier =
    if (mirrorsToFaceRight(emoji, deviceEmojiSet)) graphicsLayer(scaleX = -1f) else this

/** The emoji sets whose animals face different ways. */
internal enum class EmojiSet { SAMSUNG, OTHER }

/**
 * Whether [emoji] must be mirrored to face right in [set]. Google's set (Android) and Apple's
 * (the design canvas) draw all five speed animals facing left. Samsung's, which its phones draw
 * with instead of Google's, draws the snail facing right and the other four facing left, so
 * there the snail is left as drawn (seen on a Galaxy A55, One UI, 2026-11-04).
 */
internal fun mirrorsToFaceRight(emoji: String, set: EmojiSet): Boolean =
    !(set == EmojiSet.SAMSUNG && emoji == SNAIL)

/** Samsung phones put their own emoji font ahead of Google's in the system font fallback. */
private val deviceEmojiSet: EmojiSet by lazy {
    if (File(SAMSUNG_EMOJI_FONT).exists()) EmojiSet.SAMSUNG else EmojiSet.OTHER
}

private const val SAMSUNG_EMOJI_FONT = "/system/fonts/SamsungColorEmoji.ttf"
private const val SNAIL = "🐌" // TempoBand.SNAIL
