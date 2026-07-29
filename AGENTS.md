# AGENTS.md

## Fog of World WebApp - Agent Guidelines

This document provides guidelines for AI agents, contributors, and developers working on the **Fog of World WebApp** project.

---

## 📌 Project Overview

**Fog of World WebApp** is a real-life game where users reveal a fog-covered world map by exploring the real world. The app tracks user movements via GPS or imported tracks (GPX/KML) and gradually uncovers the map, providing statistics, achievements, and a leveling system.

### Key Features
- **Fog Reveal**: Vector-based fog removal with a 15m radius around tracks or GPS points.
- **GPX/KML Import**: Users can import existing tracks to reveal fog along those paths.
- **Live GPS Tracking**: Real-time fog reveal using the browser's Geolocation API.
- **Achievement System**: 8 default achievements for milestones like distance traveled, area revealed, or level reached.
- **Level System**: XP-based progression (10 XP per km² revealed, level up every 500 XP).
- **Statistics**: Track revealed area (km²), percentage of the world revealed, and total distance.
- **PWA Support**: Offline-capable Progressive Web App with service workers.
- **Open-Source Only**: Uses OpenStreetMap, Leaflet.js, Turf.js, and Font Awesome.

---

## 🛠️ Development Guidelines

### Code Style
- **JavaScript**: Use ES6+ syntax (e.g., `const`, `async/await`, arrow functions).
- **Indentation**: 2 spaces (no tabs).
- **Naming**: Use `camelCase` for variables/functions and `PascalCase` for classes.
- **Comments**: Add comments for non-obvious logic or complex algorithms (e.g., fog reveal calculations).

### File Structure
```
fog-of-world-web/
├── index.html              # Main HTML file (entry point)
├── package.json            # Dependencies and scripts
├── vite.config.js          # Vite + PWA configuration
├── .gitignore              # Ignored files (node_modules, dist)
├── public/
│   └── manifest.json       # PWA manifest
└── src/
    └── main.js             # Core app logic
```

### Dependencies
- **Allowed**: Open-source libraries only (e.g., Leaflet, Turf.js, Font Awesome).
- **Avoid**: Proprietary APIs or closed-source tools unless absolutely necessary.
- **Add New Dependencies**: Justify the need in a PR description.

---

## 🤖 Agent-Specific Instructions

### For AI Agents (e.g., Vibe Code)
1. **Scope**: Always confirm the scope of changes before implementing. Ask clarifying questions if the request is ambiguous.
2. **Testing**: 
   - Test changes locally using `npm run dev`.
   - Verify GPX/KML imports, live tracking, and fog reveal functionality.
3. **Commits**: 
   - Use descriptive commit messages (e.g., "Add GPX parser for track imports").
   - Reference issues or features in commit messages (e.g., "Closes #123").
4. **Pull Requests**: 
   - Include a clear description of changes.
   - Add screenshots or GIFs for UI changes.
   - Link to relevant issues or discussions.

### Example Workflow for Agents
1. **User Request**: "Add a new achievement for 50 km² revealed."
2. **Agent Action**:
   - Add the achievement to `DEFAULT_ACHIEVEMENTS` in `src/main.js`.
   - Test by importing a large GPX file to trigger the achievement.
   - Commit with message: "Add 50 km² achievement to default list."

---

## 📦 Build & Deployment

### Local Development
```bash
cd fog-of-world-web
npm install          # Install dependencies
npm run dev         # Start dev server (http://localhost:3000)
```

### Production Build
```bash
npm run build       # Generates optimized files in dist/
```

### Deployment Options
1. **GitHub Pages**:
   ```bash
   npm run build
   git add dist -f
   git commit -m "Add built files"
   git subtree push --prefix dist origin gh-pages
   ```
   - Enable GitHub Pages in repo settings (Branch: `gh-pages`).

2. **Netlify/Vercel**:
   - Drag and drop the `dist/` folder to the platform.
   - Or connect the repo and set the build command to `npm run build`.

---

## 🐛 Bug Reporting & Fixes

### How to Report Bugs
1. **Describe the Issue**: Include steps to reproduce, expected vs. actual behavior.
2. **Environment**: Browser, OS, and device (if mobile).
3. **Logs**: Console errors or warnings (check browser DevTools).
4. **Screenshots**: For UI issues, include visuals.

### Common Issues & Fixes
| **Issue**                          | **Possible Fix**                                                                                     |
|------------------------------------|-----------------------------------------------------------------------------------------------------|
| Fog not revealing on import         | Check if GPX/KML parser is correctly extracting points. Verify `revealFogAlongTrack()` is called.   |
| GPS tracking not working            | Ensure browser has geolocation permissions. Test on HTTPS or localhost.                              |
| Achievements not unlocking         | Debug `checkAchievements()` logic. Verify stats (area/distance) are updating correctly.             |
| Map tiles not loading               | Check OpenStreetMap URL in `L.tileLayer()`. Ensure internet connection.                              |
| IndexedDB errors                    | Clear browser data or check for quota limits.                                                      |

---

## 🔧 Technical Details

### Fog Reveal Algorithm
- **Radius**: 15 meters (configurable via `REVEAL_RADIUS` in `main.js`).
- **Method**: 
  1. For each point in a track, create a 15m buffer polygon using Turf.js.
  2. Union all polygons into a single shape to avoid overlaps.
  3. Save the polygon to IndexedDB and render it as a fog-free area on the map.
- **Performance**: Uses vector polygons (not raster) for precision and scalability.

### Data Storage (IndexedDB)
- **Stores**:
  - `fogPolygons`: Revealed fog areas (polygon coordinates + metadata).
  - `tracks`: Imported or recorded tracks (points, color, distance).
  - `achievements`: User achievements (unlocked status, timestamps).
  - `userLevel`: XP, current level, and next level threshold.
  - `stats`: Total revealed area, distance, and percentage of the world.

### PWA Configuration
- **Service Worker**: Caches OpenStreetMap tiles for offline use.
- **Manifest**: Defines app name, icons, and theme colors.
- **Offline Support**: Works without internet after first load (except live GPS).

---

## 📜 Contributing

### How to Contribute
1. **Fork the Repository**: Create a fork of `Imacikus/OpenFog-Online`.
2. **Create a Branch**: Use a descriptive name (e.g., `feature/add-new-achievements`).
3. **Commit Changes**: Follow the [commit guidelines](#code-style).
4. **Open a PR**: Link to the issue (if applicable) and describe your changes.

### PR Template
```markdown
## Summary
- [x] Added feature X
- [x] Fixed bug Y

## Changes
- Modified `src/main.js` to add new achievement logic.
- Updated `index.html` to include new UI elements.

## Testing
- Tested GPX import with sample files.
- Verified fog reveal works on live tracking.

## Screenshots (if applicable)
![Screenshot](url)
```

---

## 📄 License
This project is open-source and uses the following licenses:
- **Code**: MIT License (default for the repository).
- **Dependencies**: Respect the licenses of all used libraries (e.g., Leaflet, Turf.js).

---

## 🆘 Support
- **Questions**: Open a GitHub Discussion.
- **Bugs**: Open an Issue with details.
- **Feature Requests**: Open an Issue with a clear description.

---

## 🔗 Useful Links
- [Repository](https://github.com/Imacikus/OpenFog-Online)
- [Leaflet.js Docs](https://leafletjs.com/reference.html)
- [Turf.js Docs](https://turfjs.org/docs/)
- [Font Awesome Icons](https://fontawesome.com/icons)
- [IndexedDB API](https://developer.mozilla.org/en-US/docs/Web/API/IndexedDB_API)
