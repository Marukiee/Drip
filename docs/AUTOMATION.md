# Automation

Tasker, MacroDroid or adb can put their own things on the island with a broadcast. Drip ignores every broadcast until
**Allow automation** is on in its settings.

Android only delivers these broadcasts when they name Drip's package: `nl.markmaaktmedia.drip`, or
`nl.markmaaktmedia.drip.debug` for a debug build. Without the package the broadcast silently goes nowhere.

## Actions

| Action | What it does |
| --- | --- |
| `nl.markmaaktmedia.drip.SHOW` | Shows a short announcement, or adds or updates a lasting item |
| `nl.markmaaktmedia.drip.HIDE` | Removes the lasting item with that `id` |

## Extras

All extras may be sent as text. Anything out of range is clamped, anything unreadable is ignored.

| Extra | Meaning |
| --- | --- |
| `id` | Required. Up to 64 characters. Showing the same `id` again updates it |
| `title` | Up to 80 characters. Without a title, `text` becomes the title. One of the two is required |
| `text` | A second line, up to 240 characters |
| `kind` | `alert` (default): a short announcement. `live`: stays until `HIDE` |
| `icon` | `timer`, `music`, `bolt`, `notifications`, `check`, `warning`, `home`, `car`, `alarm`, `battery`, `call`, `mail`, `message`, `info`, `calendar`, `location`, `download`, `upload`, `sports`, `heart`, `computer`, `phone`, `coffee`, `wifi`, `bluetooth`, `lock`, `headphones`, `camera`, `mic`, `sleep` |
| `color` | `#RRGGBB` (or `#AARRGGBB`) |
| `progress` | 0 to 100, draws a bar |
| `seconds` | A countdown from now, up to 7 days |
| `duration` | How long an announcement stays, 1000 to 60000 ms. Default: the setting in Drip |
| `package` | An app whose icon to show, opened on a tap |

## adb

```sh
adb shell am broadcast -a nl.markmaaktmedia.drip.SHOW -p nl.markmaaktmedia.drip \
  --es id oven --es kind live --es title Pizza --es text "Almost done" \
  --es icon timer --es color "#FF8800" --es seconds 600

adb shell am broadcast -a nl.markmaaktmedia.drip.HIDE -p nl.markmaaktmedia.drip --es id oven
```

## Tasker

Task, action **System > Send Intent**:

- Action: `nl.markmaaktmedia.drip.SHOW`
- Extra: `id:washer`
- Extra: `title:Washing machine`
- Extra: `progress:%WASH_PERCENT`
- Package: `nl.markmaaktmedia.drip`
- Target: Broadcast Receiver

Each SHOW replaces the item with that `id` as a whole, so send every extra each time. Send Intent has three extra
fields; for more, use **Code > Run Shell** with the `am broadcast` line from the adb example (without `adb shell`).
End it with a Send Intent of `nl.markmaaktmedia.drip.HIDE` and `id:washer`.
