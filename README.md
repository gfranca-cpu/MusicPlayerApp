# Music Player App

App de música focado na **lista de Artistas expansível** com animação bem lisa.

## Características principais

- **Tela de Artistas** (foco do app):
  - Lista de artistas em faixas cinza-azuladas
  - Toque para expandir/recolher com animação suave
  - Mostra álbum + músicas
  - Ao tocar na música → toca **sem mudar de tela**
  - Nome da música tocando fica verde

- **Navegação inferior**:
  - Artistas | Álbuns | Músicas

- **Mini Player** fixo embaixo (quando tem música tocando)

- Lê as músicas reais do armazenamento do celular (MediaStore)

## Como abrir no Android Studio

1. Abra o Android Studio
2. **File → Open** e selecione a pasta `MusicPlayerApp`
3. Espere o Gradle sincronizar
4. Crie um emulador ou conecte um celular
5. Clique em **Run**

## Permissões

Na primeira execução o app pede permissão para acessar as músicas.  
Aceite para ele carregar as músicas do celular.

## Tecnologias

- Kotlin
- Jetpack Compose
- Media3 (ExoPlayer)
- Material 3
