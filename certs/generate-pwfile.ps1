# 重新生成MQTT密码文件
# 使用已知的用户名和密码

$certDir = "C:\work\examples\mqtt-examples\certs"
$pwfile = "$certDir\pwfile"

# 检查是否安装了mosquitto
$mosquittoPasswd = Get-Command mosquitto_passwd -ErrorAction SilentlyContinue

if ($mosquittoPasswd) {
    # 如果安装了mosquitto工具，使用它生成密码文件
    Write-Host "Generating password file using mosquitto_passwd..."
    
    # 删除旧的pwfile
    if (Test-Path $pwfile) {
        Remove-Item $pwfile -Force
    }
    
    # 创建新的密码文件，用户名为mqtt_user，密码为public
    & mosquitto_passwd -c $pwfile mqtt_user
    # 注意：这会提示输入密码，需要手动输入"public"
    
    Write-Host "Password file generated at: $pwfile"
} else {
    Write-Host "mosquitto_passwd not found. Please install Mosquitto or manually create the password file."
    Write-Host "Alternatively, you can use a pre-generated hash."
}
