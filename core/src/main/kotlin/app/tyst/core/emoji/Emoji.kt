package app.tyst.core.emoji

/** A compact emoji set for the offline emoji panel, ordered within each category. */
object Emoji {
    data class Category(val id: String, val label: String, val icon: String, val emoji: List<String>)

    private fun list(s: String) = s.trim().split(Regex("\\s+"))

    val CATEGORIES = listOf(
        Category("smileys", "Smileys", "😊", list("😀 😃 😄 😁 😆 😅 😂 🤣 🙂 🙃 😉 😊 😇 🥰 😍 🤩 😘 😗 😚 😙 😋 😛 😜 🤪 😝 🤗 🤭 🤫 🤔 🤐 🤨 😐 😑 😶 😏 😒 🙄 😬 😌 😔 😪 😴 😷 🤒 🤕 🥵 🥶 😵 🤯 🥳 😎 🤓 😕 😟 🙁 😮 😯 😲 😳 🥺 😦 😧 😨 😰 😥 😢 😭 😱 😖 😣 😞 😓 😩 😫 🥱 😤 😡 😠 🤬")),
        Category("gestures", "People", "👍", list("👍 👎 👌 🤌 ✌️ 🤞 🤟 🤘 🤙 👈 👉 👆 👇 ☝️ 👋 🤚 🖐️ ✋ 🖖 👏 🙌 👐 🤲 🤝 🙏 ✍️ 💪 🫶 👀 🧠 🫡 🤷 🤦 🙋 🙇 💁 🙆 🙅 👶 🧒 👦 👧 🧑 👨 👩 🧓 👴 👵")),
        Category("hearts", "Hearts", "❤️", list("❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 💯 ✨ 🔥 ⭐ 🌟 💫 🎉 🎊 🎈 🎁")),
        Category("nature", "Nature", "🌲", list("🌞 🌝 🌚 🌙 ⛅ 🌧️ ⛈️ ❄️ ☃️ 🌈 🌊 🌲 🌳 🌴 🌵 🌷 🌸 🌹 🌻 🌼 🍁 🍂 🍃 🐶 🐱 🐭 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🐔 🐧 🐦 🦆 🦉 🐴 🦄 🐝 🦋 🐌 🐞 🐟 🐬 🐳 🦌 🫎")),
        Category("food", "Food", "🍕", list("☕ 🍵 🥛 🍺 🍻 🍷 🥂 🍸 🍹 🧃 🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🫐 🍒 🍑 🥝 🍅 🥑 🥦 🥕 🌽 🥔 🍞 🥐 🥖 🧀 🥚 🍳 🥞 🧇 🥓 🍔 🍟 🍕 🌭 🥪 🌮 🌯 🥗 🍝 🍜 🍲 🍣 🍱 🍤 🍦 🍰 🎂 🧁 🍪 🍫 🍬 🍭 🍩")),
        Category("activity", "Activity", "⚽", list("⚽ 🏀 🏈 ⚾ 🎾 🏐 🏓 🏸 🏒 ⛳ 🎿 ⛷️ 🏂 🏋️ 🚴 🏊 🧘 🏃 🎮 🎲 🧩 🎯 🎳 🎤 🎧 🎸 🎹 🎺 🎻 🥁 🎬 🎨 📚 ✏️ 📝")),
        Category("travel", "Travel", "✈️", list("🚗 🚕 🚌 🚎 🚑 🚒 🚲 🛴 🛵 🏍️ 🚂 🚆 🚇 🚊 ✈️ 🛫 🛬 🚀 🛳️ ⛵ 🚤 ⚓ 🗺️ 🏠 🏡 🏢 🏥 🏦 🏨 🏫 ⛪ 🏰 🗼 🗽 ⛰️ 🏔️ 🏕️ 🏖️ 🏝️ 🌅 🌆 🌃")),
        Category("objects", "Objects", "💡", list("⌚ 📱 💻 ⌨️ 🖥️ 🖨️ 📷 📞 ☎️ 📺 ⏰ ⏳ 💡 🔦 🕯️ 💸 💵 💶 💳 🧾 ✉️ 📧 📦 📅 📆 📌 📎 ✂️ 🔒 🔓 🔑 🛠️ 🔧 🔨 🧹 🧺 🧻 🛏️ 🛁 🚿 🎒 👓 🕶️ 👕 👖 👗 🧥 🧣 🧤 👟 👠 🎓 💍")),
        Category("symbols", "Symbols", "✅", list("✅ ☑️ ✔️ ❌ ❎ ➕ ➖ ➗ ✖️ ❓ ❔ ❗ ❕ ⚠️ 🚫 ⛔ 🔴 🟠 🟡 🟢 🔵 🟣 ⚫ ⚪ 🟥 🟩 🟦 ⬆️ ⬇️ ⬅️ ➡️ 🔁 🔄 ▶️ ⏸️ ⏹️ 🆗 🆕 🆒 🔔 🔕 💤 ♻️ ©️ ®️ ™️ 🇸🇪 🇬🇧 🇺🇸 🇪🇺")),
    )

    val ALL: List<String> by lazy { CATEGORIES.flatMap { it.emoji } }

    /** The user's own most used emoji first (spec: "Emoji ordered by the user's own habits"). */
    fun recent(usage: Map<String, Int>, limit: Int = 24): List<String> =
        usage.entries.sortedByDescending { it.value }.map { it.key }.take(limit)
}
