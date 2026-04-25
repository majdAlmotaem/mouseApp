# gui_mouse_server.py
import tkinter as tk
import threading
import os
import socket
from pc_mouse_server import MouseServer

# --- Farbpalette (Futuristischer Dark/Neon Mode) ---
BG_MAIN = "#0A0D14"      # Sehr dunkles Blau-Grau (Hintergrund)
BG_PANEL = "#111520"     # Etwas helleres Panel (für Buttons)
TITLE_BAR = "#05070A"    # Noch dunkler für die Titelleiste
NEON_CYAN = "#00F0FF"    # Das leuchtende Cyan aus der App
NEON_RED = "#FF2A55"     # Das leuchtende Rot vom Disconnect-Button
TEXT_WHITE = "#FFFFFF"   # Standard Text
TEXT_MUTED = "#4A5A75"   # Ausgegrauter Text für Status

def get_ip_address():
    """Get the local IP address of the machine"""
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except:
        try:
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

def create_neon_button(parent, text, command, neon_color):
    border_frame = tk.Frame(parent, bg=neon_color)
    btn = tk.Button(
        border_frame,
        text=text.upper(),
        command=command,
        bg=BG_MAIN,
        fg=neon_color,
        activebackground=neon_color,
        activeforeground=BG_MAIN,
        font=("Segoe UI", 11, "bold"),
        bd=0,
        relief="flat",
        cursor="hand2",
        padx=25,
        pady=8
    )
    btn.pack(padx=1, pady=1) 
    return border_frame, btn

def main():
    controller = ServerController()
    
    def on_client_connected(ip):
        client_label.config(text=f"Connected to {ip}", fg=NEON_CYAN)
        
    def on_client_disconnected():
        client_label.config(text="Waiting for connection...", fg=TEXT_MUTED)
    
    root = tk.Tk()
    
    # 1. Standard OS-Rahmen entfernen
    root.overrideredirect(True)
    
    # 2. Fenstergröße und Position festlegen
    window_width = 600
    window_height = 400
    screen_width = root.winfo_screenwidth()
    screen_height = root.winfo_screenheight()
    center_x = int((screen_width - window_width) / 2)
    center_y = int((screen_height - window_height) / 2)
    root.geometry(f'{window_width}x{window_height}+{center_x}+{center_y}')
    root.configure(bg=BG_MAIN)
    
    
    
    # Der innere Container, der den 1px Rand sichtbar macht
    inner_window = tk.Frame( bg=BG_MAIN)
    inner_window.pack(fill=tk.BOTH, expand=True, padx=1, pady=1)
    
    # 4. CUSTOM TITELLEISTE BAUEN
    title_bar = tk.Frame(inner_window, bg=TITLE_BAR, height=30)
    title_bar.pack(fill=tk.X, side=tk.TOP)
    title_bar.pack_propagate(False) # Verhindert, dass die Leiste schrumpft
    
    # App Icon in der Titelleiste (optional)
    # window_icon = tk.Label(title_bar, text="💠", bg=TITLE_BAR, fg=NEON_CYAN)
    # window_icon.pack(side=tk.LEFT, padx=(10, 5))
    
    # App Name
    window_title = tk.Label(title_bar, text="LAZY CONTROLLER", bg=TITLE_BAR, fg=TEXT_MUTED, font=("Segoe UI", 9, "bold"))
    window_title.pack(side=tk.LEFT, padx=10)
    
    # Schließen Button (X)
    def close_app():
        controller.stop_server()
        root.destroy()
        
    close_btn = tk.Button(title_bar, text="✕", bg=TITLE_BAR, fg=TEXT_MUTED, 
                          activebackground=NEON_RED, activeforeground=TEXT_WHITE, 
                          bd=0, font=("Segoe UI", 10), command=close_app, cursor="hand2")
    close_btn.pack(side=tk.RIGHT, padx=5, fill=tk.Y)
    
    # 5. FENSTER VERSCHIEBEN LOGIK (Drag & Drop)
    offset_x = 0
    offset_y = 0

    def start_move(event):
        nonlocal offset_x, offset_y
        offset_x = event.x
        offset_y = event.y

    def do_move(event):
        x = root.winfo_pointerx() - offset_x
        y = root.winfo_pointery() - offset_y
        root.geometry(f"+{x}+{y}")

    # Binde die Maus-Events an die Titelleiste
    title_bar.bind("<Button-1>", start_move)
    title_bar.bind("<B1-Motion>", do_move)
    window_title.bind("<Button-1>", start_move)
    window_title.bind("<B1-Motion>", do_move)

    # --- MAIN CONTENT ---
    main_frame = tk.Frame(inner_window, bg=BG_MAIN)
    # Relx/Rely positionieren es exakt in der Mitte des inner_window
    main_frame.place(relx=0.5, rely=0.5, anchor="center")

    title_font = ("Segoe UI", 22, "bold")
    status_font = ("Segoe UI", 11)

    # Titel
    title_label = tk.Label(
        main_frame,
        text="SERVER CONTROL",
        font=title_font,
        bg=BG_MAIN,
        fg=TEXT_WHITE
    )
    title_label.pack(pady=(0, 5))
    
    line = tk.Frame(main_frame, bg=NEON_CYAN, height=2, width=60)
    line.pack(pady=(0, 30))

    # Status indicator
    status_frame = tk.Frame(main_frame, bg=BG_MAIN)
    status_frame.pack(pady=(0, 30))

    status_label = tk.Label(status_frame, text="STATUS:", font=("Segoe UI", 11, "bold"), bg=BG_MAIN, fg=TEXT_MUTED)
    status_label.pack(side=tk.LEFT, padx=(0, 10))

    status_text = tk.Label(status_frame, text="OFFLINE", font=("Segoe UI", 11, "bold"), bg=BG_MAIN, fg=NEON_RED)
    status_text.pack(side=tk.LEFT)

    # Buttons frame
    btn_frame = tk.Frame(main_frame, bg=BG_MAIN)
    btn_frame.pack(pady=20)

    # Info Labels
    info_frame = tk.Frame(main_frame, bg=BG_MAIN)
    info_frame.pack(pady=(20, 0))
    
    server_label = tk.Label(info_frame, text="Server IP: Not running", font=status_font, bg=BG_MAIN, fg=TEXT_MUTED)
    server_label.pack(pady=(0, 5))
    
    client_label = tk.Label(info_frame, text="No device connected", font=status_font, bg=BG_MAIN, fg=TEXT_MUTED)
    client_label.pack()

    # Callbacks
    controller.on_client_connected = lambda ip: root.after(0, on_client_connected, ip)
    controller.on_client_disconnected = lambda: root.after(0, on_client_disconnected)

    def on_start():
        if controller.start_server():
            server_ip = get_ip_address()
            status_text.config(text="ONLINE", fg=NEON_CYAN)
            server_label.config(text=f"Server IP: {server_ip}:9999", fg=TEXT_WHITE)
            client_label.config(text="Waiting for connection...", fg=TEXT_MUTED)

    def on_stop():
        controller.stop_server()
        status_text.config(text="OFFLINE", fg=NEON_RED)
        server_label.config(text="Server IP: Not running", fg=TEXT_MUTED)
        client_label.config(text="No device connected", fg=TEXT_MUTED)

    start_border, start_btn = create_neon_button(btn_frame, "Start Server", on_start, NEON_CYAN)
    start_border.pack(side=tk.LEFT, padx=15)

    stop_border, stop_btn = create_neon_button(btn_frame, "Stop Server", on_stop, NEON_RED)
    stop_border.pack(side=tk.LEFT, padx=15)

    root.mainloop()

if __name__ == "__main__":
    main()