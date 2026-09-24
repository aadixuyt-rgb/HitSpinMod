# Hit Spin — Fabric 1.21.4

Autor / branding: **Adixu_YT**

## Funkcje
- Fabric 1.21.4 client mod.
- Domyślny keybind `N`; można go zmienić w Minecraft → Opcje → Sterowanie → Klawisze.
- GUI z zaokrąglonymi panelami, animowanymi/kolorowymi elementami i 6 motywami: Ryba, Żaba, Smok, Lis, Kot, Panda.
- Czas pojedynczego obrotu: **0,01–4,00 s**.
- Wpisywanie konkretnej wartości w polu nad suwakiem; suwak synchronizuje się z wartością.
- 9 rodzajów obrotu: 360°, lewo, prawo, góra, dół oraz cztery skosy.
- Losowanie spośród zaznaczonych typów albo wykonywanie ich po kolei.
- Efekt uruchamia się tylko po ataku w `PlayerEntity`, nie po kliknięciu powietrza.
- Po każdym obrocie innym niż 360° kamera wraca do pozycji startowej przez dokładnie taki sam czas.
- 360° nie wykonuje osobnego powrotu.
- Ustawienia zapisują się do `config/hitspin.json`.

## Budowanie
Wymaga JDK 21 i Gradle/Gradle Wrapper. W katalogu projektu uruchom:

`gradle build`

Gotowy plik JAR znajdzie się w `build/libs/`.

W projekcie użyto Yarn `1.21.4+build.8`, Fabric Loader `0.16.10` i Fabric API `0.119.4+1.21.4`.
