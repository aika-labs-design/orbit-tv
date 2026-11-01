# Third-party notices

Orbit's own code is MIT licensed (see [LICENSE](LICENSE)). This file lists every third-party asset bundled in this repository and in the app, with its licence and the attribution it requires, followed by the services Orbit uses at runtime. Licence texts that must travel with an asset are included below or next to the asset.

## Bundled in the app

### Fonts (SIL Open Font License 1.1)

All five font families are licensed under the SIL Open Font License, Version 1.1. Each font's licence is stated in its own name table (checked for every file) and a copy of the licence with its copyright line sits next to it in `app/src/main/assets/web/fonts/`.

| File | Font | Copyright | Licence file |
|---|---|---|---|
| `fonts/Inter-Variable.ttf` | Inter 4.001 | Copyright 2016 The Inter Project Authors (https://github.com/rsms/inter) | `OFL-Inter.txt` |
| `fonts/CormorantGaramond-Variable.ttf` | Cormorant Garamond 4.001 | Copyright 2015 The Cormorant Project Authors (github.com/CatharsisFonts/Cormorant) | `OFL-Cormorant.txt` |
| `fonts/Unbounded.ttf` | Unbounded 1.701 | Copyright 2022 The Unbounded Project Authors (https://github.com/googlefonts/unbounded) | `OFL-Unbounded.txt` |
| `fonts/SpaceGrotesk.ttf` | Space Grotesk 2.000 | Copyright 2020 The Space Grotesk Project Authors (https://github.com/floriankarsten/space-grotesk) | `OFL-SpaceGrotesk.txt` |
| `fonts/SpaceMono-Regular.ttf`, `fonts/SpaceMono-Bold.ttf` | Space Mono 1.003 | Copyright 2016 The Space Mono Project Authors (https://github.com/googlefonts/spacemono) | `OFL-SpaceMono.txt` |

The fonts are unmodified. The licence text (identical for all five):

```
-----------------------------------------------------------
SIL OPEN FONT LICENSE Version 1.1 - 26 February 2007
-----------------------------------------------------------

PREAMBLE
The goals of the Open Font License (OFL) are to stimulate worldwide
development of collaborative font projects, to support the font creation
efforts of academic and linguistic communities, and to provide a free and
open framework in which fonts may be shared and improved in partnership
with others.

The OFL allows the licensed fonts to be used, studied, modified and
redistributed freely as long as they are not sold by themselves. The
fonts, including any derivative works, can be bundled, embedded, 
redistributed and/or sold with any software provided that any reserved
names are not used by derivative works. The fonts and derivatives,
however, cannot be released under any other type of license. The
requirement for fonts to remain under this license does not apply
to any document created using the fonts or their derivatives.

DEFINITIONS
"Font Software" refers to the set of files released by the Copyright
Holder(s) under this license and clearly marked as such. This may
include source files, build scripts and documentation.

"Reserved Font Name" refers to any names specified as such after the
copyright statement(s).

"Original Version" refers to the collection of Font Software components as
distributed by the Copyright Holder(s).

"Modified Version" refers to any derivative made by adding to, deleting,
or substituting -- in part or in whole -- any of the components of the
Original Version, by changing formats or by porting the Font Software to a
new environment.

"Author" refers to any designer, engineer, programmer, technical
writer or other person who contributed to the Font Software.

PERMISSION & CONDITIONS
Permission is hereby granted, free of charge, to any person obtaining
a copy of the Font Software, to use, study, copy, merge, embed, modify,
redistribute, and sell modified and unmodified copies of the Font
Software, subject to the following conditions:

1) Neither the Font Software nor any of its individual components,
in Original or Modified Versions, may be sold by itself.

2) Original or Modified Versions of the Font Software may be bundled,
redistributed and/or sold with any software, provided that each copy
contains the above copyright notice and this license. These can be
included either as stand-alone text files, human-readable headers or
in the appropriate machine-readable metadata fields within text or
binary files as long as those fields can be easily viewed by the user.

3) No Modified Version of the Font Software may use the Reserved Font
Name(s) unless explicit written permission is granted by the corresponding
Copyright Holder. This restriction only applies to the primary font name as
presented to the users.

4) The name(s) of the Copyright Holder(s) or the Author(s) of the Font
Software shall not be used to promote, endorse or advertise any
Modified Version, except to acknowledge the contribution(s) of the
Copyright Holder(s) and the Author(s) or with their explicit written
permission.

5) The Font Software, modified or unmodified, in part or in whole,
must be distributed entirely under this license, and must not be
distributed under any other license. The requirement for fonts to
remain under this license does not apply to any document created
using the Font Software.

TERMINATION
This license becomes null and void if any of the above conditions are
not met.

DISCLAIMER
THE FONT SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO ANY WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT
OF COPYRIGHT, PATENT, TRADEMARK, OR OTHER RIGHT. IN NO EVENT SHALL THE
COPYRIGHT HOLDER BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
INCLUDING ANY GENERAL, SPECIAL, INDIRECT, INCIDENTAL, OR CONSEQUENTIAL
DAMAGES, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
FROM, OUT OF THE USE OR INABILITY TO USE THE FONT SOFTWARE OR FROM
OTHER DEALINGS IN THE FONT SOFTWARE.
```

### Three.js (MIT)

`app/src/main/assets/web/lib/three.min.js` is Three.js r128, used only by the screensaver and the globe renderer. Its header reads "Copyright 2010-2021 Three.js Authors, SPDX-License-Identifier: MIT".

```
The MIT License

Copyright © 2010-2021 three.js authors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
```

### Earth and sky images

| Files | Source | Licence and credit |
|---|---|---|
| `web/world/earth_day.jpg`, `web/world/earth_night.jpg`, `web/world/earth_clouds.jpg` | NASA Visible Earth: Blue Marble (day), Black Marble (night) and cloud imagery | NASA imagery is not protected by copyright in the United States (public domain). Credit: NASA. NASA's name and insignia are not used to imply endorsement. |
| `web/globe/f00.jpg` to `f71.jpg` | Rendered by this project from the three NASA textures above with Three.js (`tools/globe/`) | As above. Credit: NASA. |
| `web/world/milkyway.jpg` | ESO, "The Milky Way panorama", eso0932a, https://www.eso.org/public/images/eso0932a/ | Creative Commons Attribution 4.0 International (https://creativecommons.org/licenses/by/4.0/). Credit: **ESO/S. Brunier**. |

### Home Mode sounds (Creative Commons)

From Wikimedia Commons, trimmed, faded, loudness-normalised and re-encoded as AAC (those are the changes made). Listed in `app/src/main/assets/web/homes/ATTRIBUTION.md`:

| File | Source title | Author | Licence | Page |
|---|---|---|---|---|
| sounds/rain.m4a | Heavy rain in Glenshaw, PA.ogg | Sage Ross | CC BY-SA 3.0 | https://commons.wikimedia.org/wiki/File:Heavy_rain_in_Glenshaw,_PA.ogg |
| sounds/temple.m4a | Thakil & Nadaswaram during temple Deeparadhana.ogg | Vis M | CC BY 4.0 | https://commons.wikimedia.org/wiki/File:Thakil_%26_Nadaswaram_during_temple_Deeparadhana.ogg |
| sounds/birds.m4a | RainforestDawnChorus Selaliparai DM.ogg | DivyaCM | CC BY 4.0 | https://commons.wikimedia.org/wiki/File:RainforestDawnChorus_Selaliparai_DM.ogg |
| sounds/night.m4a | Ringing cricket winding down.ogg | Jidanni | CC BY-SA 4.0 | https://commons.wikimedia.org/wiki/File:Ringing_cricket_winding_down.ogg |

CC BY 4.0: https://creativecommons.org/licenses/by/4.0/ . CC BY-SA 3.0: https://creativecommons.org/licenses/by-sa/3.0/ . CC BY-SA 4.0: https://creativecommons.org/licenses/by-sa/4.0/ . The two CC BY-SA recordings, as adapted here, are shared under the same licence as their source.

### Places, countries, time zones (GeoNames, CC BY 4.0)

`app/src/main/assets/countries.json` and `app/src/main/assets/place-names.json` are generated by `tools/data/build_countries.py` from GeoNames data kept in `tools/data/src/` (`countryInfo.txt`, `timeZones.txt`, `cities.tsv` trimmed from `cities15000.txt`, `city-names.tsv` from `cities15000.txt` and `admin1CodesASCII.txt`). Licence: Creative Commons Attribution 4.0 International. Credit: **"Contains data from GeoNames (geonames.org), CC BY 4.0."** Changes: trimmed to the largest places per country plus capitals, reduced to the columns Orbit uses. See `tools/data/src/LICENSE-geonames.txt`.

### Language and country names (Unicode CLDR, Unicode License v3)

Language names (`tools/data/src/languages.tsv`, used in `languages.json`) and country names in their own languages (`tools/data/src/country-names.tsv`) come from Unicode CLDR 48, read through Node's `Intl.DisplayNames`. The licence (also in `tools/data/src/LICENSE-unicode-cldr.txt`):

```
UNICODE LICENSE V3

COPYRIGHT AND PERMISSION NOTICE

Copyright © 1991-2026 Unicode, Inc.

NOTICE TO USER: Carefully read the following legal agreement. BY
DOWNLOADING, INSTALLING, COPYING OR OTHERWISE USING DATA FILES, AND/OR
SOFTWARE, YOU UNEQUIVOCALLY ACCEPT, AND AGREE TO BE BOUND BY, ALL OF THE
TERMS AND CONDITIONS OF THIS AGREEMENT. IF YOU DO NOT AGREE, DO NOT
DOWNLOAD, INSTALL, COPY, DISTRIBUTE OR USE THE DATA FILES OR SOFTWARE.

Permission is hereby granted, free of charge, to any person obtaining a
copy of data files and any associated documentation (the "Data Files") or
software and any associated documentation (the "Software") to deal in the
Data Files or Software without restriction, including without limitation
the rights to use, copy, modify, merge, publish, distribute, and/or sell
copies of the Data Files or Software, and to permit persons to whom the
Data Files or Software are furnished to do so, provided that either (a)
this copyright and permission notice appear with all copies of the Data
Files or Software, or (b) this copyright and permission notice appear in
associated Documentation.

THE DATA FILES AND SOFTWARE ARE PROVIDED "AS IS", WITHOUT WARRANTY OF ANY
KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT OF
THIRD PARTY RIGHTS.

IN NO EVENT SHALL THE COPYRIGHT HOLDER OR HOLDERS INCLUDED IN THIS NOTICE
BE LIABLE FOR ANY CLAIM, OR ANY SPECIAL INDIRECT OR CONSEQUENTIAL DAMAGES,
OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS,
WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION,
ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THE DATA
FILES OR SOFTWARE.

Except as contained in this notice, the name of a copyright holder shall
not be used in advertising or otherwise to promote the sale, use or other
dealings in these Data Files or Software without prior written
authorization of the copyright holder.
```

### Other data in `tools/data/src`

- `netflix-countries.tsv`: the distinct country names and codes from the first two columns of Netflix's public Top 10 file (https://www.netflix.com/tudum/top10/data/all-weeks-countries.tsv). Facts (country names and ISO codes), used to match Netflix's names; the file itself is not kept.
- `news-editions.tsv`: which Google News editions exist, checked live by `tools/data/verify_news.py`. Facts (edition codes) only.

### Project-made assets

Created for Orbit and covered by its MIT License: the mood backdrop frames in `web/frames/` (generated procedurally by the project), the app banner `res/drawable/banner.png`, the curated lists in `places.json`, `aliases.json`, `interests.json`, `feeds.json`, `handoffs.json`, `providers.json`, `channels/`, `festivals/` and `homes/local.json`. These lists hold facts and names (places, coordinates, team and league names, channel ids, public holiday dates) and, in `interests.json` and `places.json`, links to Wikimedia images that are loaded at runtime, not bundled.

## Bundled in the repository only (not in the app)

- **Gradle wrapper** (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`): Copyright 2015 the original authors, Gradle, Inc. Apache License 2.0 (https://www.apache.org/licenses/LICENSE-2.0); the licence text is inside the jar at `META-INF/LICENSE`.
- **Test fixtures** (`app/src/test/resources/feeds/`): short recorded responses from the public services listed below (TMDB, YouTube, Google News, ESPN, Cricinfo, Jolpica, Open-Meteo, Nager.Date, Radio Browser, Wikipedia and Wikidata, TVmaze, The Race, the Netflix Top 10 file), kept only so the tests run offline. Their content belongs to those services and their contributors (Wikipedia text is CC BY-SA 4.0); they are not part of the app.

## Services used at runtime

Orbit loads these at runtime on the TV; nothing from them is bundled. Use them under their own terms.

| Service | Attribution and terms |
|---|---|
| TMDB | "This product uses the TMDB API but is not endorsed or certified by TMDB." The TMDB logo (`app/src/main/assets/web/tmdb-logo.svg`, TMDB's approved "blue short" logo) and this notice are shown in Cinema. The logo is a trademark of TMDB, used under its attribution rules (https://www.themoviedb.org/about/logos-attribution). TMDB's API terms apply; Orbit is for personal, non-commercial use. https://www.themoviedb.org/api-terms-of-use |
| JustWatch (through TMDB) | Where-to-watch data is JustWatch's, provided through TMDB; credited in Cinema ("Streaming data from JustWatch"). |
| YouTube | Videos play in YouTube's embedded player and are credited to their channel on screen. YouTube API Services Terms of Service and the YouTube Terms of Service apply: https://developers.google.com/youtube/terms/api-services-terms-of-service |
| Wikipedia, Wikimedia Commons, Wikidata | Place summaries and photographs are hotlinked at runtime and credited on screen ("Photograph from Wikipedia, Wikimedia Commons"). Text is CC BY-SA 4.0; each photograph keeps its own licence and author on its Commons page. Wikidata is CC0. |
| Google News RSS | Headlines link to and name their publishers. |
| ESPN, Cricinfo | Unofficial public endpoints, used for scores only; may change without notice. |
| Jolpica | Formula 1 data, https://github.com/jolpica/jolpica-f1 |
| Open-Meteo | Weather and geocoding, data under CC BY 4.0; credited on screen ("Weather Open-Meteo"). https://open-meteo.com/en/license |
| Nager.Date | Public holidays, https://date.nager.at |
| Radio Browser | Community radio directory, https://www.radio-browser.info |
| Netflix Top 10 | Netflix's public weekly Top 10 file. |
| TVmaze | Show posters; see its API licence at https://www.tvmaze.com/api |
| The Race | MotoGP news RSS. |
| ipwho.is, ipapi.co | The country and approximate location the TV's connection is seen from. |

All names and trademarks above belong to their owners. Orbit is not affiliated with or endorsed by any of them.
