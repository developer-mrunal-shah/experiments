# Learning Lab — Synology NAS Setup Guide

## What's in this folder

```
learning-lab/
├── index.html                  ← Library homepage (auto-discovers collections)
├── docker-compose.yml          ← Docker config for Synology
├── nginx.conf                  ← Web server config
├── add-collection.sh           ← Script to register new collections
├── SETUP.md                    ← This file
└── collections/
    ├── manifest.json           ← Auto-generated list of collections
    └── arduino/
        ├── meta.json           ← Collection metadata
        ├── index.html          ← Interactive web app
        └── print.html          ← Printable project sheets
```

## Option A: Deploy with Container Manager (Recommended)

This is the easiest way — uses Docker under the hood.

### Step 1: Copy files to NAS

Copy the entire `learning-lab` folder to a shared folder on your Synology. For example:

```
/volume1/docker/learning-lab/
```

You can use **File Station** (drag & drop), **SMB/network share**, or **SCP**:

```bash
scp -r learning-lab/ your-user@NAS-IP:/volume1/docker/
```

### Step 2: Create the project in Container Manager

1. Open **Container Manager** on your Synology (install from Package Center if needed)
2. Go to **Project** → **Create**
3. Set **Project name** to `learning-lab`
4. Set **Path** to `/volume1/docker/learning-lab`
5. It will auto-detect `docker-compose.yml`
6. Click **Next** → **Done**

### Step 3: Access the Learning Lab

Open a browser on any device on your network and go to:

```
http://<YOUR-NAS-IP>:8080
```

To find your NAS IP, check **Control Panel → Network → Network Interface** on your Synology.

## Option B: Deploy with Web Station

If you prefer not to use Docker:

1. Install **Web Station** from Package Center
2. Set the web root to your `learning-lab` folder
3. It will serve the static HTML files directly
4. Access at `http://<NAS-IP>` (port 80)

## Adding New Collections

### Quick method — ask Claude!

Come back and ask me to create a new collection (e.g., "Create a Python basics collection for the Learning Lab"). I'll generate the files in the right format.

### Manual method

1. Create a new folder inside `collections/`:
   ```bash
   ./add-collection.sh --template python-basics
   ```

2. Edit `collections/python-basics/meta.json` with your collection info

3. Add your `index.html` (interactive app) and optionally `print.html`

4. Run the discovery script:
   ```bash
   ./add-collection.sh
   ```

5. Refresh the Learning Lab homepage — your new collection appears!

### meta.json format

```json
{
  "title": "Python Basics",
  "icon": "🐍",
  "color": "#3b82f6",
  "description": "Learn Python programming from scratch.",
  "age": "10-14",
  "projectCount": 10,
  "difficulty": "Beginner",
  "tags": ["python", "coding", "programming"],
  "pages": {
    "interactive": "index.html",
    "printable": "print.html"
  },
  "created": "2026-02-16",
  "author": "Palani's Learning Lab"
}
```

## Accessing from specific devices

### iPad / Tablet
Just open Safari or Chrome and go to `http://<NAS-IP>:8080`. Bookmark it or add to Home Screen for app-like access.

### Bookmark on kids' devices
On any browser, navigate to the URL and:
- **iOS**: Share → Add to Home Screen
- **Android**: Menu → Add to Home Screen
- **Chrome**: Menu → Install app (if available)

## Troubleshooting

**Can't access the page?**
- Check the container is running in Container Manager
- Verify port 8080 isn't used by another service
- Try a different port: change `8080:80` in docker-compose.yml

**New collection not showing?**
- Make sure `meta.json` exists in the collection folder
- Run `./add-collection.sh` to rebuild the manifest
- Hard-refresh the browser (Ctrl+Shift+R)

**Want a different port?**
Edit `docker-compose.yml` and change `8080:80` to `YOUR_PORT:80`
