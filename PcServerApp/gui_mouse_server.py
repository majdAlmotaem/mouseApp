# gui_mouse_server.py
# Hinweis: Installiere tkinter mit: pip install tk (falls nicht vorhanden)
# Dieses GUI startet und stoppt den Mouse Server

import tkinter as tk
from tkinter import messagebox
import threading
import os
import sys
import socket
from pc_mouse_server import MouseServer

def get_ip_address():
    """Get the local IP address of the machine"""
    try:
        # Connect to an external server (Google DNS) to determine local IP
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except:
        try:
            # Fallback: use hostname
            return socket.gethostbyname(socket.gethostname())
        except:
            return "localhost"

class ServerController:
    def __init__(self):
        self.server = MouseServer()
        self.server_thread = None
        self.on_client_connected = None
        self.on_client_disconnected = None

    def start_server(self):
        if not self.server_thread:
            self.server.on_client_connected = self.on_client_connected
            self.server.on_client_disconnected = self.on_client_disconnected
            self.server_thread = threading.Thread(target=self.server.start, daemon=True)
            self.server_thread.start()
            return True
        return False

    def stop_server(self):
        if self.server_thread:
            self.server.stop()
            self.server_thread = None


def main():
    controller = ServerController()
    
    def on_client_connected(ip):
        client_label.config(text=f"Connected device: {ip}", fg="#44ff44")
        
    def on_client_disconnected():
        client_label.config(text="Waiting for connection...", fg="#888888")
    
    controller.on_client_connected = lambda ip: root.after(0, on_client_connected, ip)
    controller.on_client_disconnected = lambda: root.after(0, on_client_disconnected)

    root = tk.Tk()
    root.title("Lazy Controller")
    # Add icon
    try:
        icon_path = os.path.join(os.path.dirname(__file__), "icon.ico")
        root.iconbitmap(icon_path)
    except:
        print("Could not load icon file")
    
    root.geometry("600x400")
    root.configure(bg="#1e1e1e")
    root.minsize(500, 350)
    
    # Fenster in der Bildschirmmitte positionieren
    window_width = 600
    window_height = 400
    screen_width = root.winfo_screenwidth()
    screen_height = root.winfo_screenheight()
    center_x = int((screen_width - window_width) / 2)
    center_y = int((screen_height - window_height) / 2)
    root.geometry(f'{window_width}x{window_height}+{center_x}+{center_y}')

    # Modern fonts
    title_font = ("Segoe UI", 24, "bold")
    status_font = ("Segoe UI", 12)
    btn_font = ("Segoe UI", 12, "bold")

    # Main container
    main_frame = tk.Frame(root, bg="#1e1e1e")
    main_frame.place(relx=0.5, rely=0.5, anchor="center")

    # Title
    title_label = tk.Label(
        main_frame,
        text="Mouse Server Control",
        font=title_font,
        bg="#1e1e1e",
        fg="white"
    )
    title_label.pack(pady=(0, 40))

    # Status indicator
    status_frame = tk.Frame(main_frame, bg="#1e1e1e")
    status_frame.pack(pady=(0, 30))

    status_label = tk.Label(
        status_frame,
        text="• Status:",
        font=status_font,
        bg="#1e1e1e",
        fg="#888888"
    )
    status_label.pack(side=tk.LEFT, padx=(0, 10))

    status_text = tk.Label(
        status_frame,
        text="Stopped",
        font=status_font,
        bg="#1e1e1e",
        fg="#ff4444"
    )
    status_text.pack(side=tk.LEFT)

    # Button styles
    btn_style = {
        "font": btn_font,
        "width": 10,
        "height": 1,
        "bd": 0,
        "relief": "flat",
        "cursor": "hand2",
        "padx": 20
    }

    # Buttons frame
    btn_frame = tk.Frame(main_frame, bg="#1e1e1e")
    btn_frame.pack(pady=20)

    # Add server info label (IP address)
    server_frame = tk.Frame(main_frame, bg="#1e1e1e")
    server_frame.pack(pady=(0, 10))
    
    server_label = tk.Label(
        server_frame,
        text="Server IP: Not running",
        font=status_font,
        bg="#1e1e1e",
        fg="#888888"
    )
    server_label.pack()

    # Add client info label
    client_frame = tk.Frame(main_frame, bg="#1e1e1e")
    client_frame.pack(pady=(0, 20))
    
    client_label = tk.Label(
        client_frame,
        text="No device connected",
        font=status_font,
        bg="#1e1e1e",
        fg="#888888"
    )
    client_label.pack()

    def on_start():
        if controller.start_server():
            server_ip = get_ip_address()
            status_text.config(text="Running", fg="#44ff44")
            server_label.config(text=f"Server IP: {server_ip}:9999", fg="#44ff44")
            client_label.config(text="Waiting for connection...", fg="#888888")

    def on_stop():
        controller.stop_server()
        status_text.config(text="Stopped", fg="#ff4444")
        server_label.config(text="Server IP: Not running", fg="#888888")
        client_label.config(text="No device connected", fg="#888888")

    start_btn = tk.Button(
        btn_frame,
        text="Start Server",
        command=on_start,
        bg="#2d89ef",
        fg="white",
        activebackground="#1b5fa7",
        activeforeground="white",
        **btn_style
    )
    start_btn.pack(side=tk.LEFT, padx=10)

    stop_btn = tk.Button(
        btn_frame,
        text="Stop Server",
        command=on_stop,
        bg="#ff4444",
        fg="white",
        activebackground="#cc3333",
        activeforeground="white",
        **btn_style
    )
    stop_btn.pack(side=tk.LEFT, padx=10)

    root.protocol("WM_DELETE_WINDOW", lambda: (controller.stop_server(), root.destroy()))
    root.mainloop()


if __name__ == "__main__":
    main()


