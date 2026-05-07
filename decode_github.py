import base64
import urllib.request
import json

def fetch_github_file():
    # GitHub API URL for glasses_manager.py
    url = "https://api.github.com/repos/mf858796-dev/bishe/contents/glasses_manager.py"

    try:
        with urllib.request.urlopen(url) as response:
            data = json.loads(response.read().decode())

        # Decode base64 content
        content = base64.b64decode(data['content']).decode('utf-8')

        print("=" * 60)
        print("glasses_manager.py - Tobii Pro Glasses 3 Manager")
        print("=" * 60)
        print(content)
        print("\n" + "=" * 60)

        # Key features from this code:
        print("\nKey Features:")
        print("- Uses g3pylib library for Tobii Pro Glasses 3 connection")
        print("- Supports both IP and ZeroConf connection modes")
        print("- Streams RTSP data including scene camera and gaze data")
        print("- Processes gaze2d data from the glasses")
        print("- Async implementation for non-blocking operations")
        print("\nGaze Data Format:")
        print("- gaze2d: [x, y] coordinates")
        print("- timestamp: gaze data timestamp")
        print("- frame_array: scene camera frame data")

    except Exception as e:
        print(f"Error: {e}")

if __name__ == "__main__":
    fetch_github_file()
