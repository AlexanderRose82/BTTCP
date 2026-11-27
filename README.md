# BTTCP
Bluetooth-Server-Bridge — Android foreground service bridging a classic Bluetooth SPP/RFCOMM device (such as an ELM327 OBD-II adapter) to a local TCP server (127.0.0.1:35000), built via GitHub Actions with configurable MAC address, host, port, and intent controls.

# What the App Actually Does
In plain terms, BTTCP acts as a local middleman on Android:
It opens a Bluetooth SPP/RFCOMM connection (00001101-0000-1000-8000-00805F9B34FB) to a target Bluetooth device (like an ELM327 adapter).
It runs a local TCP server (for example on 127.0.0.1:35000).
Any local client (such as a Python script in Termux) connects to that TCP port, sends raw text/AT/OBD commands (e.g., 015B\r), and receives the raw bytes coming back from the Bluetooth device—without needing /dev/rfcomm* or Bluetooth socket support inside the client runtime.

## How It Works

1. **Bluetooth SPP Client:** Connects over RFCOMM (SPP UUID `00001101-0000-1000-8000-00805F9B34FB`) to a target Bluetooth device MAC address.
2. **Local TCP Server:** Listens on a configured host and port (default: `127.0.0.1:35000`) and forwards bidirectional byte streams between TCP clients and the Bluetooth serial connection.
3. **Foreground Service & Automation:** Runs as an Android foreground service so background clients (such as Python scripts in Termux) can query the device reliably without Android killing the connection immediately.

## App Permissikons 

On Android 12 and newer, the app needs the BLUETOOTH_CONNECT permission (plus legacy BLUETOOTH / BLUETOOTH_ADMIN for older Android versions) to connect to your paired Bluetooth device, as well as FOREGROUND_SERVICE and FOREGROUND_SERVICE_CONNECTED_DEVICE (and POST_NOTIFICATIONS on Android 13+) to stay alive reliably in the background with a status notification.

## Roadmap

- **v0.1 (Personal / Hardcoded):** Hardcoded target Bluetooth MAC address, listen host, and TCP port tailored to a single setup, built in the cloud via GitHub Actions.
- **v0.2+ (General Purpose):** UI to pick any paired Bluetooth device, configure host/port, manage reconnect behavior, and start/stop via intents or quick toggles.

## Building via GitHub Actions

This repository is designed to be edited directly on GitHub.com and built on a GitHub Actions runner (`ubuntu-latest`) without installing the Android SDK locally. Download the resulting APK from the workflow **Artifacts** after a build completes.

## License

Released into the public domain—see the `LICENSE` file for details.

## Disclaimer

Created with LLM/AI assistance.
