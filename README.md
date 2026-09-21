# ITDO для Android (Material Design 1)

Нативная оболочка вокруг сайта `https://itdo.bleyzos.ru`: тулбар и боковое меню в стиле
Material Design 1 (AppCompat), сам сайт работает в WebView.

* Нижняя навигация и меню «Ещё» убраны — все разделы в сайдбаре в стиле Gmail:
  баннер, аватар, имя и @username, до двух других аккаунтов справа, стрелка ▾ со списком
  аккаунтов и «Добавить аккаунт», ниже — разделы со значками-счётчиками
  (уведомления, сообщения, кошелёк). Тема меню следует за темой сайта (тёмная/светлая).
* FAB «+» на ленте открывает окно публикации.
* Поддержаны: загрузка файлов, камера/микрофон (звонки, трансляции), полноэкранное видео,
  confirm()/alert(), скачивание файлов, вход через Яндекс (popup), deep-links на домен сайта.

## Сборка
Android Studio (Ladybug+) → Open → папка проекта → Run.
Из консоли: `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.

## Настройка
* Адрес сайта: `siteHost` в `app/build.gradle`.
* Скрипт, прячущий веб-навигацию и передающий состояние в приложение:
  `app/src/main/assets/itdo-android.js`.
* Пункты меню: `DrawerItem.java`.

Иконки — Material Icons (Google, Apache 2.0).

## Сборка APK в облаке
Залейте проект в репозиторий GitHub — workflow `.github/workflows/build.yml` соберёт
`app-debug.apk` (вкладка Actions → Artifacts). Локально ничего ставить не нужно.
