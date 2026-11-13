# SkyProfile Viewer (Fabric, Minecraft 26.2)

In-game SkyBlock profile viewer with a NEU-style layout, using SkyCrypt (sky.shiiyu.moe) as the data source.

## Using it

- Click a player's name in Hypixel chat → in the social options message, click **[View Profile]** (added after **[Block Player]**).
- Or type `/skyprofile <name>` (`/skyprofile` alone opens your own profile).
- `/skyprofile apikey` (or the ⚙ button on the viewer) sets your SkyCrypt API key.
- Tabs: Basic Info, Dungeons, Extra Info, Inventories, Collections, Pets. Keys 1-6 switch tabs.
- Bottom bar: search another player, switch profiles, open the profile on the SkyCrypt website.

## SkyCrypt API key

SkyCrypt's API needs a personal key: log in at sky.shiiyu.moe → dashboard → developer page → request a key.
It is stored in `.minecraft/config/skyprofile.json` and only sent to `api.shiiyu.moe`.

SkyCrypt's API only returns players that someone looked up on the website recently. If a player shows
"not cached", open them once on SkyCrypt (the viewer has a button for it) and press Retry.

## Building

Needs a Java 25 JDK. The Minecraft launcher ships one, so from this folder:

```bash
JAVA_HOME="$LOCALAPPDATA/Packages/Microsoft.4297127D64EC6_8wekyb3d8bbwe/LocalCache/Local/runtime/java-runtime-epsilon/windows-x64/java-runtime-epsilon" ./gradlew build
```

The jar ends up in `build/libs/skyprofile-1.0.0.jar`. It needs Fabric API installed.

Dev testing with mock data (no API key needed): `./gradlew runClient -PdevShot` screenshots every tab into `run/screenshots/`.

Not affiliated with or endorsed by Hypixel or SkyCrypt.
