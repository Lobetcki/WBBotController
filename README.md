# WB Bot Controller – Android‑приложение для автоматизации заказов Wildberries

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-blue.svg)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-14-green.svg)](https://developer.android.com)
[![Telegram Bot API](https://img.shields.io/badge/Telegram%20Bot%20API-9.6.0-blue)](https://core.telegram.org/bots/api)

**WB Bot Controller** – это мобильное приложение, которое превращает Telegram‑бота в полноценного ассистента продавца Wildberries. 
Бот автоматически отслеживает новые заказы, отправляет уведомления в Telegram, создаёт поставки, генерирует стикеры и листы подбора (CSV). 
Всё это работает даже когда приложение свёрнуто или телефон выключен – благодаря Android WorkManager и Foreground Service.

## 🚀 Возможности

- **Получение заказов** – через официальное API Wildberries (песочница / боевой контур).
- **Telegram‑уведомления** – бот присылает детали заказа, количество, дату.
- **Автоматическое создание поставок** – добавляет заказы в существующую поставку или создаёт новую.
- **Генерация листа подбора** – выгружает заказы в CSV‑файл (совместим с Excel) и отправляет в чат.
- **Генерация QR‑кодов и стикеров** – для передачи в доставку.
- **Передача поставки в доставку** – полный цикл FBS.
- **Поддержка тем (topics)** – бот корректно отвечает в нужной подгруппе.
- **Работа в фоне** – периодическая проверка заказов через WorkManager.
- **Автоматический старт после перезагрузки** – через `BootReceiver`.
- **Конфигурация через удобный интерфейс** – токены, интервалы
## 🧰 Технологии

| Компонент | Технологии |
|-----------|------------|
| **Язык** | Kotlin |
| **UI** | Jetpack Compose (Material 3) |
| **Фоновая работа** | WorkManager, Foreground Service |
| **HTTP‑клиент** | OkHttp + Gson |
| **Асинхронность** | Kotlin Coroutines (runBlocking, delay) |
| **Telegram API** | `kotlin-telegram-bot` (dev.inmo.tgbotapi) |
| **Локальное хранилище** | SharedPreferences (PreferencesManager) |
| **Параметры сборки** | Gradle Kotlin DSL, Version Catalog |
| **Разрешения** | POST_NOTIFICATIONS, INTERNET, RECEIVE_BOOT_COMPLETED |

## 📦 Установка

1. Скачайте APK‑файл из раздела [Releases]() или соберите проект в Android Studio.
2. Установите приложение на устройство с Android 7.0+.
3. В настройках укажите:
   - Токен Telegram‑бота (получить у [@BotFather](https://t.me/BotFather))
   - API‑токен Wildberries (боевой или тестовый, с правами **Заказы** и **Поставки**)
4. Нажмите «Включить» и напишите боту **СТАРТ** в Telegram – чат активируется.
5. Готово! Бот начнёт отслеживать заказы и присылать уведомления.

## 🛠️ Сборка из исходников

```bash
git clone https://github.com/Lobetcki/WBBotController.git
cd WBBotController
# Открыть проект в Android Studio
# Подключить телефон/эмулятор
# Нажать Run
