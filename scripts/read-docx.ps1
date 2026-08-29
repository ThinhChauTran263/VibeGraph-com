param([string]$Path)
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($Path)
$entry = $zip.Entries | Where-Object { $_.FullName -eq 'word/document.xml' }
$stream = $entry.Open()
$reader = New-Object System.IO.StreamReader($stream)
$xml = [xml]$reader.ReadToEnd()
$reader.Close()
$stream.Close()
$zip.Dispose()

$ns = New-Object System.Xml.XmlNamespaceManager($xml.NameTable)
$ns.AddNamespace('w', 'http://schemas.openxmlformats.org/wordprocessingml/2006/main')

$paragraphs = $xml.SelectNodes('//w:p', $ns)
foreach ($p in $paragraphs) {
    $texts = $p.SelectNodes('.//w:t', $ns)
    $line = ($texts | ForEach-Object { $_.'#text' }) -join ''
    if ($line -ne '') {
        Write-Output $line
    }
}
