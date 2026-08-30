# Custom Routes Privacy Policy

- Effective date: 2026-08-26

Custom Routes is designed to process route photos locally. The app does not
operate an account service, analytics service, advertising service, or backend
that receives photos, projects, masks, or usage events.

## Data Stored On The Device

The app stores the following in Android app-private storage:

- Private copies of photos explicitly selected through Android's photo picker.
- Route project names, hold masks, roles, and edit metadata.
- The downloaded EfficientSAM model used for local AI segmentation.
- Global role-color and AI behavior preferences.
- A temporary full-resolution JPEG while the export preview is open. It is
  deleted after saving or leaving preview; stale candidates are removed when
  the app starts again.

Image embeddings used for AI segmentation are transient, memory-only derived
data. They are released when the project closes or the app is terminated and are
not persisted, backed up, or transferred.

Android backup and device transfer are disabled for this data. Android removes
it when the app is uninstalled or when the user chooses Delete all local data.

## Network Use

The app connects to the internet only after the user confirms the one-time AI
model download. It retrieves pinned EfficientSAM artifacts from Hugging Face and
approved Hugging Face content-delivery hosts over HTTPS. Downloaded files are
verified by expected size and SHA-256 digest before use.

ONNX Runtime network telemetry is disabled explicitly, and its telemetry startup
component and network-state permission are removed from the merged app manifest.

Photos, project data, masks, and AI prompts are not sent with this request.
Hugging Face and its delivery infrastructure receive ordinary connection
metadata such as the device's IP address, request headers, and request time,
subject to their own policies.

## Photo Access And Exports

The app uses Android's system photo picker and receives access only to photos
the user selects. It does not request broad photo-library or storage permission.

Exports are new JPEG files written to `Pictures/Custom Routes`. They are public
media and may be indexed, shared, or synchronized by gallery and cloud-photo
apps according to those apps' settings. In-app deletion never removes original
photos or exported JPEGs.

The export preview is generated first in app-private cache. It becomes public
media only when the user selects Save JPEG or Save anyway.

## Diagnostics

Production and internal builds do not emit the app's performance Logcat
messages. Debug builds used for local development may log model load time,
inference duration, memory usage, and prompt count. They do not log image data,
filenames, prompt coordinates, or masks.

## User Controls

Privacy & Data settings allow the user to:

- Delete the downloaded AI model and any partial download.
- Delete all local projects and their private photo copies.
- Reset global role colors.
- Delete all local app data through Android's app-data clearing mechanism.

Original photos and exported JPEGs are preserved by every action.

## Policy Changes

Material changes to the app's data behavior must update this policy and its
effective date.
