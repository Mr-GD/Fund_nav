# 基金净值估算安卓应用

这是一个使用WebView封装Streamlit应用的安卓项目，用于在手机上访问基金净值估算系统。

## 准备工作

1. **运行Streamlit应用**
   - 在电脑上打开命令提示符/终端
   - 进入项目目录：`cd e:\Project\Fund_nav`
   - 运行应用：`streamlit run app.py`
   - 查看终端输出，找到类似 `http://localhost:8501` 的地址

2. **获取本地IP地址**
   - Windows：打开命令提示符，输入 `ipconfig`，找到"无线局域网适配器 WiFi"下的"IPv4地址"
   - macOS/Linux：打开终端，输入 `ifconfig` 或 `ip addr`，找到本地IP

3. **修改安卓代码中的IP地址**
   - 打开 `android/app/src/main/java/com/example/fundnavapp/MainActivity.java`
   - 将 `webView.loadUrl("http://192.168.1.100:8501");` 中的IP地址改为你的本地IP

## 构建APK

1. **使用Android Studio打开项目**
   - 启动Android Studio
   - 选择"Open an existing project"
   - 导航到 `e:\Project\Fund_nav\android` 目录并打开

2. **同步项目**
   - Android Studio会自动检测并提示同步Gradle项目
   - 点击"Sync Now"完成同步

3. **构建APK**
   - 在顶部菜单栏选择 `Build > Build Bundle(s) / APK(s) > Build APK(s)`
   - 等待构建完成
   - 构建完成后，点击"locate"按钮找到生成的APK文件

4. **安装到手机**
   - 将生成的APK文件传输到你的安卓手机
   - 在手机上打开APK文件并安装
   - 确保手机和电脑连接到同一WiFi网络

## 运行应用

1. **在电脑上启动Streamlit应用**
   ```bash
   cd e:\Project\Fund_nav
   streamlit run app.py
   ```

2. **在手机上打开应用**
   - 点击手机桌面上的"基金净值估算"图标
   - 应用会自动加载电脑上运行的Streamlit应用

## 注意事项

- 确保电脑和手机连接到同一WiFi网络
- 电脑需要保持运行状态，Streamlit应用需要持续运行
- 如需在没有电脑的情况下使用，需要将Streamlit应用部署到云服务器

## 故障排除

- **应用无法加载**：检查本地IP地址是否正确，确保Streamlit应用正在运行
- **网络错误**：检查手机和电脑是否在同一网络，尝试关闭防火墙
- **构建失败**：确保Android Studio已安装所有必要的SDK和构建工具
