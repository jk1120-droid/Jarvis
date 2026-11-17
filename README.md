# Jarvis — soukromý offline pomocník pro Android

Jarvis je český offline startovací projekt v Kotlinu a Jetpack Compose. Obsahuje lokální výukové režimy (učitel, programování a filmová tvorba), hlasový vstup pouze přes Android on-device speech recognition, volitelné hlasové odpovědi a místní denní přehled oznámení.

## Soukromí

- Aplikace nemá oprávnění INTERNET a neposílá chat ani oznámení do cloudové AI. Online režim byl odstraněn, protože cloudová AI by vyžadovala odesílat jí zprávy.
- Záloha aplikace je vypnutá. Zachycená oznámení se ukládají do soukromých lokálních předvoleb aplikace (nejvýše 200 položek) a zpracovávají se pouze v zařízení.
- Přístup k oznámením je citlivé oprávnění Androidu a je zcela dobrovolný. Uživatel ho musí výslovně povolit v systémovém nastavení; lze ho kdykoli odebrat.
- Hlasový vstup se používá jen pokud zařízení podporuje on-device rozpoznávání (Android 12+). Je potřeba mít stažený český offline jazykový balíček. Pokud není dostupný, Jarvis místo toho zobrazí zprávu a lze psát.
- Pro hlasový výstup použij v Androidu nainstalovaný offline český hlas. Dostupnost offline hlasu závisí na zařízení a jeho TTS enginu.

## Denní přehled oznámení

V aplikaci zvol **Zapnout přehled**, povol oznámení Jarvise a klepni na **Povolit přístup**. Android otevře systémovou stránku; tam povol Jarvisovi přístup k oznámením. Aplikace uloží zachycený text lokálně a každý den přibližně ve 20:00 zobrazí souhrnné oznámení. Čas může být kvůli úsporným funkcím Androidu mírně nepřesný. Klepnutím na **Dnešní přehled** zobrazíš text souhrnu v aplikaci; zapnutý hlasový výstup jej přečte nahlas.

## Spuštění

Otevři projekt v Android Studiu s podporou Android SDK 35, proveď Gradle Sync a spusť `app` na zařízení s Android 8.0 nebo novějším. Jarvis je zatím lokální tutor se základními odpověďmi, nikoli plnohodnotný generativní AI model. Zachycení notifikací i hlasové funkce je nutné ověřit na skutečném zařízení; vyžadují odpovídající systémová oprávnění a místní jazykové balíčky.
