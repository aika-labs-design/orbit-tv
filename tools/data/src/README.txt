Sources for tools/data/build_countries.py (run it after changing anything here).

countryInfo.txt, timeZones.txt   GeoNames dumps, 31 Oct 2026. CC BY 4.0, see LICENSE-geonames.txt.
cities.tsv                       Trimmed from GeoNames cities15000.txt: python3 tools/data/build_countries.py --trim cities15000.txt
languages.tsv                    ISO 639-1 codes with English and native names from CLDR 48 (Unicode License v3, LICENSE-unicode-cldr.txt),
                                 read through Node's Intl.DisplayNames, first letter capitalised in the language's own locale.
                                 Codes are the 2-letter ones used by countryInfo.txt (nb and nn folded into no).
netflix-countries.tsv            Distinct country names and codes from the first two columns of Netflix's public Top 10 file
                                 (https://www.netflix.com/tudum/top10/data/all-weeks-countries.tsv). The file itself is not kept.
news-editions.tsv                Google News editions checked live by tools/data/verify_news.py (31 Oct 2026).
country-names.tsv                Each country's name in its own languages (CLDR through Node's Intl.DisplayNames): node tools/data/country_names.js
city-names.tsv                   For the cities in cities.tsv: first-level region (GeoNames admin1CodesASCII.txt) and other Latin-script names
                                 (GeoNames cities15000.txt alternate names), CC BY 4.0:
                                 python3 tools/data/build_countries.py --city-names cities15000.txt admin1CodesASCII.txt
                                 The build writes them to app/src/main/assets/place-names.json (read only by the app, for home news).

Credit: "Contains data from GeoNames (geonames.org), CC BY 4.0."
