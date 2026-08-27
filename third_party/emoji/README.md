# OpenTypeless Emoji inventory provenance

`scripts/generate_emoji_catalog.py` deterministically generates the checked-in
`EmojiCatalogData.java`. It contains 1,898 distinct fully-qualified Unicode Emoji 15.1 base
sequences across smileys, people, animals, food, activities, travel, objects, symbols and flags.
Skin-tone variants and standalone components are excluded; multi-code-point, ZWJ, keycap and flag
sequences remain available. English and Chinese names/keywords come from Unicode CLDR 45.

The maintenance generator verifies every input byte before parsing:

- `emoji-test.txt`: `d876ee249aa28eaa76cfa6dfaa702847a8d13b062aa488d465d0395ee8137ed9`
- CLDR `en.xml`: `cd86c3f805d7ee7cedd690cdb0523bdba279a89ba91aeb7982016571e77e61cb`
- CLDR `en-derived.xml`: `fefbd0d2b52ba50bf46ecf91586726ccd0ebbe74616f3d5c3dc6ab2f9317dc0d`
- CLDR `zh.xml`: `a09285afe873592b9eeeb1fa0de34a0bab79fce85351155d3427c6ec65c9b2ba`
- CLDR `zh-derived.xml`: `30b66c9c20ede0c8919588b78edb5a4f173bb2c3c69776ec59b77eb2faf9670c`

Sources:

- https://www.unicode.org/Public/emoji/15.1/emoji-test.txt
- https://github.com/unicode-org/cldr/tree/release-45/common/annotations
- https://github.com/unicode-org/cldr/tree/release-45/common/annotationsDerived

License: Unicode License v3 (`Unicode-3.0`), retained in `LICENSE.txt`. Purpose: bounded offline
Emoji browsing and search for `KBD-010`. No Unicode chart image, font, glyph artwork, runtime data
parser or network fetch is included. Updating the inventory requires an explicit reviewed version/
hash change plus generator, duplicate/count, query-bound, UI and system-selected IME tests.
