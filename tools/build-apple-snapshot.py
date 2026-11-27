#!/usr/bin/env python3
"""Rebuilds Altitude's bundled list of Apple's aerial clips from Apple's own feed.

    python tools/build-apple-snapshot.py [feed-url]

Downloads Apple's resources tar (trusting Apple Root CA, which the app bundles for the same server),
reads its entries.json and writes app/src/main/assets/apple-snapshot.json: Altitude's own clip list -
ids, place names, scenes and links - never Apple's file. The rules are the app's (AppleFeed.kt): the
scene from the asset's category, the English label as the title, HDR links left out, an asset with
an unknown scene or no https link left out. Standard library only.
"""
import io
import json
import ssl
import sys
import tarfile
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FEED_URL = "https://sylvan.apple.com/Aerials/resources-16.tar"
CA = ROOT / "app" / "src" / "main" / "res" / "raw" / "apple_root_ca.pem"
OUT = ROOT / "app" / "src" / "main" / "assets" / "apple-snapshot.json"
MAX_FEED_BYTES = 64 * 1024 * 1024
SCENES = {
    "AerialCategoryLandscapes": "LANDSCAPES",
    "AerialCategoryCities": "CITIES",
    "AerialCategoryUnderwater": "UNDERWATER",
    "AerialCategorySpace": "SPACE",
}
# Altitude's field, Apple's key.
LINKS = (("link1080H264", "url-1080-H264"), ("link1080Hevc", "url-1080-SDR"), ("link4kHevc", "url-4K-SDR"))


def text(obj, key):
    value = obj.get(key)
    return value if isinstance(value, str) and value.strip() else None


def clips_of(entries):
    scenes = {}
    for category in entries["categories"]:
        scene = isinstance(category, dict) and SCENES.get(text(category, "localizedNameKey"))
        if scene and text(category, "id"):
            scenes[category["id"]] = scene
    clips, seen = [], set()
    for asset in entries["assets"]:
        if not isinstance(asset, dict):
            continue
        clip_id, title = text(asset, "id"), text(asset, "accessibilityLabel")
        scene = next((scenes[c] for c in asset.get("categories") or [] if isinstance(c, str) and c in scenes), None)
        links = {}
        for name, key in LINKS:
            url = text(asset, key)
            if url and url.startswith("https://"):
                links[name] = url
        if not clip_id or not title or not scene or not links or clip_id in seen:
            continue
        seen.add(clip_id)
        clips.append({"id": clip_id, "title": title, "scene": scene, **links})
    return clips


def main():
    url = sys.argv[1] if len(sys.argv) > 1 else FEED_URL
    context = ssl.create_default_context(cafile=str(CA))
    with urllib.request.urlopen(url, context=context, timeout=180) as response:
        if not response.geturl().startswith("https://"):
            sys.exit("the feed was redirected off https")
        data = response.read(MAX_FEED_BYTES + 1)
    if len(data) > MAX_FEED_BYTES:
        sys.exit(f"the feed is over {MAX_FEED_BYTES} bytes")
    with tarfile.open(fileobj=io.BytesIO(data)) as tar:
        entries = json.load(tar.extractfile("entries.json"))
    clips = clips_of(entries)
    if not clips:
        sys.exit("no usable clip in the feed; the snapshot is unchanged")
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text("[\n" + ",\n".join(json.dumps(c, ensure_ascii=False) for c in clips) + "\n]\n", encoding="utf-8", newline="\n")
    per_scene = {s: sum(1 for c in clips if c["scene"] == s) for s in SCENES.values()}
    print(f"{len(clips)} clips {per_scene} from {url} -> {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
