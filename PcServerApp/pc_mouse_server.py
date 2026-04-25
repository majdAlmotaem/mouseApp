# pc_mouse_server.py
# Hinweis: Installiere pyautogui mit: pip install pyautogui
# Hinweis: Installiere pycaw mit: pip install pycaw

import socket
import threading
import pyautogui
import sys
import os
import platform
from ctypes import cast, POINTER
from comtypes import CLSCTX_ALL
from pycaw.pycaw import AudioUtilities, IAudioEndpointVolume

class MouseServer:
    def __init__(self, host='0.0.0.0', port=9999):
        self.host = host
        self.port = port
        self.server = None
        self.running = False
        self.on_client_connected = None
        self.on_client_disconnected = None
        pyautogui.FAILSAFE = False
        
        # Initialize audio controls
        try:
            devices = AudioUtilities.GetSpeakers()
            interface = devices.Activate(IAudioEndpointVolume._iid_, CLSCTX_ALL, None)
            self.volume = cast(interface, POINTER(IAudioEndpointVolume))
        except:
            self.volume = None

    def handle_client(self, conn, addr):
        if self.on_client_connected:
            self.on_client_connected(addr[0])
        try:
            while self.running:
                data = conn.recv(1024)
                if not data:
                    break
                command = data.decode('utf-8').strip()
                
                if command.startswith("MOUSE_MOVE"):
                    parts = command.split()
                    if len(parts) >= 3:
                        try:
                            dx = int(parts[1])
                            dy = int(parts[2])
                            pyautogui.moveRel(dx, dy, duration=0)  # duration=0 for instant movement
                        except:
                            pass
                elif command == "LEFT_CLICK":
                    pyautogui.click(button='left')
                elif command == "ARROW_LEFT":
                    pyautogui.press('left')
                elif command == "ARROW_RIGHT":
                    pyautogui.press('right')
                elif command == "SCROLL_UP":
                    pyautogui.scroll(120)  # Positive value scrolls up
                elif command == "SCROLL_DOWN":
                    pyautogui.scroll(-120)  # Negative value scrolls down
                elif command == "VOLUME_UP":
                    if self.volume:
                        current = self.volume.GetMasterVolumeLevelScalar()
                        new_volume = min(1.0, current + 0.02)  # Erhöhe um 2%
                        self.volume.SetMasterVolumeLevelScalar(new_volume, None)
                
                elif command == "VOLUME_DOWN":
                    if self.volume:
                        current = self.volume.GetMasterVolumeLevelScalar()
                        new_volume = max(0.0, current - 0.02)  # Reduziere um 2%
                        self.volume.SetMasterVolumeLevelScalar(new_volume, None)
                
                elif command == "SHUTDOWN":
                    self.stop()
                    # System shutdown command für Windows
                    if platform.system() == "Windows":
                        os.system("shutdown /s /t 1")
                    break
                    
        except:
            pass
        finally:
            conn.close()
            if self.on_client_disconnected:
                self.on_client_disconnected()

    def start(self):
        self.server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.server.bind((self.host, self.port))
        self.server.listen()
        self.running = True
        
        while self.running:
            try:
                conn, addr = self.server.accept()
                client_thread = threading.Thread(
                    target=self.handle_client, 
                    args=(conn, addr),
                    daemon=True
                )
                client_thread.start()
            except:
                if not self.running:
                    break

    def stop(self):
        self.running = False
        if self.server:
            try:
                # Create a dummy connection to unblock accept()
                socket.create_connection((self.host, self.port)).close()
                self.server.close()
            except:
                pass

if __name__ == "__main__":
    server = MouseServer()
    try:
        server.start()
    except KeyboardInterrupt:
        server.stop()
