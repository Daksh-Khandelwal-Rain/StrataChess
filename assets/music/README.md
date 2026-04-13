# 🎵 StrataChess Music

Place your background music file here as **`theme.mp3`**.

The game will automatically detect and loop it at startup.  
If no file is found, the game starts silently — no crash, no error dialog.

---

## Where to Find a "Game of Thrones Vibe" Track (Royalty-Free)

Look for tracks with these characteristics:

| Attribute | Target |
|---|---|
| **Instrumentation** | Cello, violin, low brass, timpani drums |
| **Key** | D minor or E minor (dark, tense) |
| **Tempo** | Slow to moderate — 60–90 BPM |
| **Style** | Epic medieval, dark orchestral, cinematic |
| **Vocals** | None preferred (pure instrumental) |

### Recommended Free Sources

| Site | What to search |
|---|---|
| [Pixabay.com/music](https://pixabay.com/music/) | "epic orchestral", "dark medieval", "cinematic battle" |
| [FreeMusicArchive.org](https://freemusicarchive.org) | Filter: Classical / Cinematic |
| [incompetech.com](https://incompetech.com) by Kevin MacLeod | Search "epic" or "dark" — huge free library, CC license |
| [OpenGameArt.org](https://opengameart.org) | "medieval", "fantasy RPG", "strategy" |

### Kevin MacLeod Recommendations (incompetech.com)
These tracks are free under the Creative Commons Attribution license:
- **"Ghost Dance"** — slow, eerie, string-heavy
- **"Darkest Child"** — tense, building, perfect for a strategy game
- **"Heavy Heart"** — brooding low strings
- **"Master of the Feast"** — ceremonial, powerful

---

## File Format Notes

- Format must be **`.mp3`** (JavaFX MediaPlayer supports mp3 natively)
- Keep the file name exactly **`theme.mp3`**
- File must be placed in this directory: `assets/music/theme.mp3`
- Recommended file size: under 10MB (a 3–5 minute track at 128kbps is ~3–5MB)

---

## Volume

The default playback volume is **35%** — atmospheric without being distracting.  
To change the volume, edit this line in `src/view/MusicPlayer.java`:

```java
mediaPlayer.setVolume(0.35); // ← change this value (0.0 to 1.0)
```