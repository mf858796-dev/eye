#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Tobii Pro Glasses 3 Gaze Data Format Documentation
Based on: show_gaze_format.py from GitHub

Tobii Pro Glasses 3 Developer Guide v1.6
"""

# Sample JSON data format from Tobii Pro Glasses 3
sample_data = {
    "type": "gaze",
    "timestamp": 681.751,
    "data": {
        "gaze2d": [0.535, 0.477],
        "gaze3d": [-32.499, -4.491, 345.359],
        "eyeleft": {
            "gazeorigin": [30.221, -11.798, -23.935],
            "gazedirection": [-0.160, 0.0432, 0.986],
            "pupildiameter": 2.653
        },
        "eyeright": {
            "gazeorigin": [-31.116, -12.392, -21.406],
            "gazedirection": [-0.011, -0.00270, 0.9999],
            "pupildiameter": 2.374
        }
    }
}

print("=" * 80)
print("Tobii Pro Glasses 3 Gaze Data Format")
print("=" * 80)

print("\n### 1. gaze2d (2D Gaze Coordinates)")
print("- Format: [x, y]")
print("- Range: [0.0, 1.0] (normalized)")
print("- (0, 0) = Top-left corner")
print("- (1, 1) = Bottom-right corner")

print("\n### 2. gaze3d (3D Gaze Coordinates)")
print("- Format: [x, y, z]")
print("- Unit: millimeters (mm)")
print("- Example: [-32.499, -4.491, 345.359]")

print("\n### 3. gaze_origin (Gaze Origin Point)")
print("- Format: [x, y, z]")
print("- Unit: millimeters (mm)")
print("- Position relative to the headset")

print("\n### 4. gaze_direction (Gaze Direction Vector)")
print("- Format: [x, y, z]")
print("- Unit vector")
print("- Direction of gaze from origin")

print("\n### 5. Pupil Diameter")
print("- Unit: millimeters (mm)")
print("- Available for both left and right eyes")

print("\n### Sample JSON:")
import json
print(json.dumps(sample_data, indent=2))

print("\n" + "=" * 80)
print("Screen Coordinate Calculation:")
print("=" * 80)

x_norm, y_norm = sample_data['data']['gaze2d']
screen_width = 1920
screen_height = 1080

screen_x = x_norm * screen_width
screen_y = y_norm * screen_height

print(f"Normalized gaze: [{x_norm:.3f}, {y_norm:.3f}]")
print(f"Screen coordinates (1920x1080): [{screen_x:.0f}, {screen_y:.0f}]")
print(f"3D gaze (mm): {sample_data['data']['gaze3d']}")
print(f"Left pupil diameter: {sample_data['data']['eyeleft']['pupildiameter']:.2f} mm")
print(f"Right pupil diameter: {sample_data['data']['eyeright']['pupildiameter']:.2f} mm")

print("\n" + "=" * 80)
print("RTSP Streaming Note:")
print("=" * 80)
print("- RTSP format uses type 99 (payload type 99)")
print("- Recommended: 50Hz (50Hz typical, 100Hz max)")
print("- Python g3pylib provides easy access to gaze data")
