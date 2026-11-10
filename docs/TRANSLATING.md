# Translating JExLingo

JExLingo's own texts (help, menus, replies, status panels) live in one YAML file per language. English and German
ship with the plugin; every other language is welcome. Chat translation itself is separate: it works for every
language your LibreTranslate instance loads, see [SELF_HOSTING.md](SELF_HOSTING.md).

## Use a language on your server

1. Copy `plugins/JExLingo/translations/en_US.yml` to `plugins/JExLingo/translations/<locale>.yml`, for example
   `fr_FR.yml`, `es_ES.yml`, `pt_BR.yml`. The name is the Minecraft locale: language in lower case, country in
   upper case.
2. Translate the texts after the colons. Keep the keys on the left as they are.
3. Restart the server. The log says `Using your own language files too: [fr_FR]`, and players whose game language
   is French see the menus in French.

Texts that are missing in your file are added in English on every start, so the file never breaks after an update;
the English lines show what is still to translate. To show every player one language regardless of their game
language, set `force-locale` in `translations/config.yml`.

## Rules for a translation

- **Placeholders** in curly braces stay exactly as they are: `{player}`, `{count}`, `{language}`. You may move them
  within the sentence.
- **Colour and format tags** stay as they are and in the same number: `<#F1F5F9>name</#F1F5F9>`, `<dark_gray>`,
  `<gradient:...>`, `<newline>`. Translate only the words between them.
- **Status markers** `[OK]`, `[!]` and `[X]` stay in English; players learn them once.
- **Command names** stay as they are (`/lingo write`, `/lingo glossary add`); they are the same in every language.
- Write the way players talk on a server: short, direct, no marketing words. Use the informal form where your
  language has one (`du` in German, `tu` in French).
- Keep lines short. Menu lore wraps badly when a line is much longer than the English one.

## Send a translation for everyone

1. Fork [JExcellence/JExLingo](https://github.com/JExcellence/JExLingo) and add your file to
   `jexlingo-common/src/main/resources/translations/`.
2. Add the locale to `EXTRA_LOCALES` in `jexlingo-common/src/main/java/de/jexcellence/lingo/LingoFiles.java`.
3. Before merging, `TranslationFilesTest` compares every bundled file with `en_US.yml` and lists missing keys,
   unknown keys, and texts whose placeholders or tags differ from English, each with the file and key. The plugin
   only builds inside its parent project, so the maintainer runs it and posts the result on your pull request. You
   can check most of it yourself by trying the file on your server as described above.
4. Open a pull request that says which language it adds and whether a native speaker read it. Fixes to an existing
   file are welcome the same way.

Open requests for new languages are tagged
[`translation`](https://github.com/JExcellence/JExLingo/issues?q=is%3Aopen+label%3Atranslation) on GitHub.

Contributions are reviewed and merged at the maintainer's discretion; see [LICENSE](../LICENSE).
