# Escaneo local de secretos y archivos prohibidos antes de commit (sin imprimir secretos)
$ErrorActionPreference = "Stop"

$forbidden = git ls-files | Where-Object {
  $_ -match '\.(jks|keystore|p12|pfx|pem|key|db|sqlite|sqlite3|env|wrbackup|apk|aab)$|keystore\.properties|signing\.properties|local\.properties'
}
if ($forbidden) {
  Write-Error ("Archivos prohibidos rastreados en Git: " + ($forbidden -join ", "))
  exit 1
}

$patterns = @(
  'BEGIN (RSA |EC |DSA |OPENSSH |ENCRYPTED )?PRIVATE KEY',
  'ghp_[A-Za-z0-9]{20,}',
  'github_pat_[A-Za-z0-9_]{20,}',
  'AKIA[0-9A-Z]{16}',
  'AIza[0-9A-Za-z_-]{35}',
  '/u3\+7QAAAAI[A-Za-z0-9+/=]{24,}'
)

$found = 0
foreach ($p in $patterns) {
  $matches = git grep -n -E $p 2>$null
  if ($matches) {
    $found += @($matches).Count
    @($matches) | ForEach-Object {
      $parts = $_ -split ':'
      Write-Warning ("Posible secreto en " + $parts[0] + ":" + $parts[1])
    }
  }
}

if ($found -gt 0) {
  Write-Error "Escaneo fallido: se detectaron $found coincidencias de secretos."
  exit 1
}

Write-Output "OK: Repositorio limpio de secretos y archivos sensibles."
