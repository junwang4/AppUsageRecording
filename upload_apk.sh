sftp -oPort=443 jun@192.168.1.99:/var/www/html <<< $'put ./app/build/outputs/apk/debug/app-debug.apk'
