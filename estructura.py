import os

IGNORAR = {"env", "node_modules", ".git", "__pycache__", ".venv","data","build",".kotlin",".gradle"}

def listar_estructura(ruta, archivo, nivel=0):
    for elemento in sorted(os.listdir(ruta)):
        if elemento in IGNORAR:
            continue
        ruta_completa = os.path.join(ruta, elemento)
        archivo.write("│   " * nivel + "├── " + elemento + "\n")
        if os.path.isdir(ruta_completa):
            listar_estructura(ruta_completa, archivo, nivel + 1)

ruta_base = os.path.join("..","EntrevistaAPPBack")  # << Ajusta aquí

if not os.path.exists(ruta_base):
    print(f"❌ ERROR: La ruta no existe -> {ruta_base}")
    exit()

with open("estructura_front.txt", "w", encoding="utf-8") as archivo:
    archivo.write("Estructura Front-OM-React:\n")
    archivo.write("================================\n\n")
    listar_estructura(ruta_base, archivo)

print("✅ Archivo 'estructura_front.txt' creado correctamente.")
