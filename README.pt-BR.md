<div align="center">

# RideLab

**Transforme seu Android em um painel de ciclismo indoor**

Rolos BLE · Treino ERG · Gravação em segundo plano · Análise local

![Android](https://img.shields.io/badge/Android-12%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4)
![Local first](https://img.shields.io/badge/Data-Local%20first-35D0BA)

[English](README.md) · [简体中文](README.zh-CN.md) · [繁體中文](README.zh-TW.md) · [日本語](README.ja.md) · [Español](README.es.md) · [Português (Brasil)](README.pt-BR.md) · [Français](README.fr.md)

</div>

RideLab é um aplicativo Android nativo feito com Kotlin e Jetpack Compose. Conecte um rolo e um sensor cardíaco, ajuste a potência, siga um treino e consulte os pedais no próprio telefone. Não exige conta nem serviço em nuvem.

## Recursos

- **Painel ao vivo:** potência, frequência cardíaca, cadência, tempo e gráficos dos últimos cinco minutos.
- **ERG manual:** entrada numérica, ajustes de ±5 W, aplicação explícita e metas salvas por rolo. Restaurar uma preferência não envia comandos.
- **Treinos:** sessões integradas, teste FTP de rampa e teste inicial sem FTP conhecido. O ERG automático está restrito aos ThinkRider X2 MAX reconhecidos.
- **Gravação em segundo plano:** um serviço em primeiro plano mantém conexões e grava no Room a aproximadamente 1 Hz ao trocar de app, bloquear ou girar a tela.
- **Controle do pedal:** iniciar, pausar, retomar e encerrar. A pausa congela tempo ativo, amostragem e progressão do treino, mantendo conexões e resistência aplicada. Encerrar mantém as conexões até a desconexão manual.
- **Histórico e análise:** grupos recolhíveis por ano/mês, exclusão individual, gráficos, continuidade, médias/máximos, NP/IF/TSS, melhores potências, zonas, W/kg e comparação histórica.
- **Energia estimada:** calorias derivadas da potência e equivalências alimentares; não é uma medição individual do metabolismo.
- **Exportação:** CSV por pedal, ZIP com todos os CSV e backup completo restaurável.
- **Janela transparente:** tempo, potência, BPM e cadência sobre outros apps, curvas recolhíveis, arraste e ajustes de tamanho, fonte e opacidade.
- **Aparência:** cinco paletas, modos claro/escuro/sistema, três layouts flutuantes e sete idiomas.

Dados ausentes não viram zero; zeros reais são preservados. FTP e peso são opcionais, salvos no início do pedal e não alterados por mudanças futuras nas configurações. NP exige potência suficientemente contínua; IF/TSS exigem o FTP salvo. As melhores potências cobrem 5/15/30 segundos e 1/5/20/60 minutos.

## Compatibilidade

As capacidades são detectadas pelos serviços e características GATT reais. Medição de potência não implica controle ERG.

| Dispositivo / protocolo | Comportamento | Verificação |
| --- | --- | --- |
| ThinkRider X2 MAX | Cycling Power + FE-C over BLE ERG, 50–2000 W e treinos automáticos existentes | Principal rolo testado fisicamente |
| Rolo FTMS | Indoor Bike Data; ERG manual com características e faixa válidas | Testes de protocolo/unitários; validação física pendente; treinos automáticos desativados |
| Outros FE-C over BLE | ERG manual conservador de 50–200 W com FEC1/FEC2/FEC3 completos | Modelos não verificados; automação desativada |
| Apenas Cycling Power | Potência e cadência quando há dados de revoluções do pedivela | Não habilita ERG; função separada de medidor externo ainda incompleta |
| Sensor cardíaco BLE | Heart Rate Service padrão, BPM UINT8/UINT16 | Operação simultânea com X2 MAX verificada; outros modelos precisam de testes |

O X2 MAX testado usa Cycling Power e FE-C, sem FTMS. Quando ambos os controles existem, FE-C tem prioridade; a meta não é enviada pelos dois. FTMS exige autorização e resposta de sucesso: a escrita GATT concluída não comprova aceitação. Reconectar não repete metas automaticamente. Não há promessa de compatibilidade universal. Veja o [guia](TRAINER_COMPATIBILITY.md).

## Primeiros passos

1. Use Android 12+ (API 31) com BLE. Instale o APK se houver em [Releases](https://github.com/JigenDaisuke66/RideLab/releases), ou execute o código pelo Android Studio. AAB não pode ser instalado diretamente como APK.
2. Ative Bluetooth e permita dispositivos próximos. Escolha rolo e sensor cardíaco separadamente.
3. Confira as leituras e informe FTP/peso se souber; pode deixar em branco. O teste inicial funciona em rolos compatíveis e salvar seu resultado exige confirmação.
4. Inicie o pedal e permita notificações. Aplique explicitamente as metas ERG manuais; após encerrar, abra o Histórico.

A janela flutuante precisa de permissão para aparecer sobre outros apps; ajuste-a na tela do pedal ou nas configurações. Teste primeiro um pedal curto e outro com tela bloqueada: políticas do telefone também influenciam o segundo plano. [Procedimento](FOREGROUND_SERVICE_VERIFICATION.md).

## Exportar e restaurar

| Arquivo | Uso | Restaurável |
| --- | --- | --- |
| `.csv` individual | Amostras de um pedal para Excel/WPS | Não |
| CSV `.zip` | `summary.csv` e um CSV por pedal encerrado | Não |
| `.ridelab` | Pedais concluídos/interrompidos, amostras e FTP/peso históricos | Sim |

Exportação em lote e restauração ficam no topo do Histórico; o CSV individual está nos detalhes. Restaurar mescla registros sem substituir os existentes e ignora duplicatas idênticas. Pedais ativos e configurações do app ficam fora do backup. Campos CSV vazios indicam ausência. O backup tem compressão e verificação de integridade, mas não criptografia, e pode conter endereços Bluetooth. [Formato e limites](HISTORY_BACKUP.md).

## Idiomas

Chinês simplificado, inglês, japonês, chinês tradicional (Taiwan), espanhol, português do Brasil e francês. O app segue o sistema por padrão e usa chinês simplificado quando o idioma não é compatível. Escolhas manuais persistem; há suporte ao idioma por aplicativo do Android.

## Desenvolvimento e testes

Use Android Studio compatível com o AGP do projeto, de preferência com JDK 21 integrado. SDK de compilação 37.1, destino 36, mínimo API 31. Veja [versões](gradle/libs.versions.toml) e [Wrapper](gradle/wrapper/gradle-wrapper.properties).

```bash
git clone https://github.com/JigenDaisuke66/RideLab.git
cd RideLab
# macOS / Linux
chmod +x gradlew
./gradlew :app:testDebugUnitTest :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin
```

Windows PowerShell:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin
```

Abra, sincronize e execute `app` em um telefone real. Os comandos não geram APK. Compilar testes de dispositivo não os executa; testes unitários não substituem validação física. O serviço é dono de BLE e gravação; Compose apresenta estado e encaminha eventos. Leia [projeto](PROJECT.md), [arquitetura UI](UI_ARCHITECTURE.md) e [roteiro](ROADMAP.md).

## Feedback e contribuição

Em [Issues](https://github.com/JigenDaisuke66/RideLab/issues), informe versão do app, telefone/Android, modelo e firmware do rolo/sensor, passos, serviços/protocolo/faixa e logs: `RideLabGatt`, `RideLabWorkout`, `RideLabOverlay`. Remova endereços e dados pessoais antes de compartilhar, se desejar. Inclua testes relevantes e traduções UI nos sete idiomas.

Sincronização em nuvem, contas, FIT/TCX, PowerMatch e simulação de inclinação ainda não estão disponíveis. Consulte os documentos de verificação para distinguir implementação de validação física.
