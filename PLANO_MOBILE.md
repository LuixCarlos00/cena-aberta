# Plano de desenvolvimento mobile — Cena Aberta

Data: 16/11/2026

## Objetivo

Entregar uma versão alpha confortável para uso pelo navegador de celulares Android e iPhone. Preservar catálogo, resenhas, diário, listas, ranking e contas existentes. Após validar os fluxos principais, preparar a instalação como PWA.

O plano assume a continuidade do frontend Next.js e dos serviços atuais. Um aplicativo nativo fica para uma decisão posterior, baseada nas necessidades que surgirem no uso real.

## Diagnóstico e limites da análise

A análise inicial foi feita no código. A primeira rodada de testes em navegador emulado está registrada abaixo. Os testes em aparelhos reais seguem pendentes.

Já existem grades responsivas, detalhes em uma coluna, campos de login com altura de 46 px, ações com ícones de 48 × 46 px e rolagem horizontal no ranking.

Os principais pontos a resolver são os alvos estreitos das meias estrelas, os seletores pequenos do ranking, a ausência de alternativa por toque para ordenar filmes dentro de uma categoria e a identificação de ações que hoje depende de dicas de foco ou mouse. Quebras no cabeçalho, nomes longos e comportamento com teclado aberto precisam de verificação visual.

O inicializador local escuta em loopback. O projeto ainda não tem manifesto ou implementação de funcionamento offline para PWA.

## Entregas e sequência

| Etapa | Prioridade | Entrega | Dependência |
|---|---|---|---|
| 1 | P0 | Diagnóstico visual reproduzível | Ambiente de teste |
| 2 | P0 | Layout, navegação e formulários mobile | Etapa 1 |
| 3 | P0 | Ranking acessível por toque e salvamento confiável | Etapas 1 e 2 |
| 4 | P0 | Avaliação e ações da obra adaptadas ao toque | Etapa 2 |
| 5 | P1 | Cards, desempenho e estados de rede | Etapas 2 a 4 |
| 6 | P0 | Validação em aparelhos e publicação do alpha web | Etapas 1 a 5 |
| 7 | P2 | Instalação como PWA | Alpha web validado em HTTPS |

P0 é necessário para aceitar o alpha mobile. P1 entra antes da publicação, com foco nos problemas medidos. P2 é uma entrega posterior.

## Etapa 1 — Diagnóstico visual e ambiente

- Criar dados fictícios com nomes longos, obras sem imagem, resenhas extensas, várias listas e categorias de ranking cheias.
- Inspecionar início, login, cadastro, recuperação, catálogo, busca, obra, perfil, diário, listas e ranking.
- Usar larguras de 320, 360, 390 e 430 px, além de paisagem e uma tela desktop para detectar regressões.
- Registrar problemas com tela, largura, passos para reproduzir, resultado esperado e imagem de evidência.
- Preparar acesso de teste por HTTPS para aparelhos reais. Ajustar URL pública, callbacks e links de e-mail conforme o ambiente escolhido.
- Se necessário para desenvolvimento em rede local, tornar o endereço de escuta configurável e expor somente o frontend. Manter os serviços internos restritos.

**Aceite:** todas as telas inspecionadas, problemas priorizados e um aparelho real acessando a versão de teste. Emulação deve ser identificada como emulação nas evidências.

## Etapa 2 — Layout, navegação e formulários

- Ajustar o cabeçalho para que marca, perfil e saída caibam ou se reorganizem de forma previsível em telas estreitas.
- Manter as categorias de catálogo acessíveis com indicação visível da seleção.
- Evitar rolagem horizontal da página inteira. Reservar a rolagem horizontal para regiões como ranking e navegação.
- Revisar textos longos, quebra de títulos, espaçamento e largura mínima dos componentes.
- Adotar alvo de toque de pelo menos 44 × 44 px para os controles principais como meta de projeto.
- Garantir foco visível e rótulos acessíveis. Usar rótulos visíveis para ações cujo significado não seja evidente pelo ícone.
- Revisar teclado de e-mail, entrada numérica de CPF, preenchimento automático, mensagens de erro e checkbox de sessão persistente.
- Validar máscara de CPF ao digitar, colar e apagar no meio do valor.
- Garantir acesso aos botões com teclado aberto e ao ampliar o texto.
- Revisar os diálogos de confirmação, incluindo foco, cancelamento e rolagem em telas baixas.
- Validar navegação pelo botão Voltar, especialmente entre catálogo, obra e perfil.

**Aceite:** nenhuma ação principal inacessível em 320 px, nenhuma sobreposição ou conteúdo cortado, campos utilizáveis com teclado aberto e diálogos operáveis por toque e teclado.

## Etapa 3 — Ranking por toque

- Aumentar o seletor ou substituí-lo por uma ação “Mover” que abra opções legíveis de categoria e posição.
- Oferecer “Mover antes” e “Mover depois”, ou controles equivalentes, para ordenar dentro da mesma categoria sem depender do arraste.
- Preservar a rolagem horizontal independente em todas as categorias e em “Sem categoria”.
- Evitar que deslizar para rolar abra a obra ou mova um filme acidentalmente.
- Manter o arraste disponível onde funcionar adequadamente. Validar os gestos em aparelhos reais antes de adotá-lo como interação principal.
- Verificar salvamento automático em mudanças sucessivas. Serializar gravações ou manter uma fila que preserve a última ordem escolhida.
- Mostrar “Salvando”, “Salvo” e erro com ação de tentar novamente. Manter alterações visíveis se a gravação falhar.
- Tratar conflito de versão entre abas sem sobrescrever silenciosamente a organização de outra sessão.
- Avisar quando houver alterações pendentes ao sair. Validar saída logo após mover um card e recarregamento após confirmação de salvamento.
- Garantir que metadados ainda não carregados não removam filmes do ranking.

**Aceite:** mover entre categorias e reordenar dentro de uma categoria usando apenas toque; recarregar e encontrar a ordem salva; falhas, conflitos e tentativas repetidas sem perda silenciosa ou duplicação de filmes.

## Etapa 4 — Avaliações e ações da obra

- Manter as estrelas visuais e oferecer uma seleção precisa das notas de 0,5 a 5 por toque, por exemplo com controle complementar de incremento de meia estrela.
- Exibir a nota selecionada de forma clara e preservar a opção de limpar.
- Tornar identificáveis “Já assisti”, “Vou ver”, “Vi novamente” e “Contém spoiler” sem exigir passar o mouse.
- Manter “Registrar sessão” habilitado somente quando “Vi novamente” estiver marcado.
- Confirmar o registro de revisualização e preservar a data e hora do clique inicial.
- Manter a primeira sessão automática ao salvar a primeira resenha aplicável, com retorno ao perfil após sucesso.
- Preservar o texto digitado se ocorrer erro. Bloquear envios repetidos enquanto uma gravação estiver em andamento.
- Verificar que editar uma resenha existente não duplica a primeira sessão.

**Aceite:** escolher todas as dez notas com precisão por toque, identificar os estados das ações e concluir os fluxos de primeira visualização e revisualização sem duplicação.

## Etapa 5 — Cards, desempenho e rede

- Uniformizar cards de catálogo, assistidos, “Vou ver”, listas e diário, mantendo data e indicação de revisualização no diário.
- Verificar imagens ausentes, títulos extensos e espaçamento dos botões de editar e excluir.
- Medir carregamento e interação em conexão limitada antes de alterar o carregamento de imagens.
- Revisar tamanho dos pôsteres solicitados, carregamento sob demanda e reserva de espaço para reduzir deslocamentos de layout.
- Verificar o volume de obras carregadas no perfil. Implementar carregamento progressivo ou paginação quando a medição indicar necessidade.
- Exibir estados de carregamento, vazio e erro recuperável nas telas principais.
- Preservar resenha e organização local do ranking em falhas recuperáveis durante a mesma sessão.

**Aceite:** imagens não deformam os cards, conteúdo principal permanece utilizável durante carregamento e uma falha de rede permite tentar novamente sem repetir registros já confirmados.

## Etapa 6 — Validação e alpha web mobile

### Matriz de verificação

| Cenário | Verificação |
|---|---|
| Android com Chrome | Toque, teclado, imagens, navegação e rolagem |
| iPhone com Safari | Toque, foco, diálogos, teclado, sessão e upload |
| Larguras de 320 a 430 px | Conteúdo, cabeçalho, cards e ausência de transbordamento da página |
| Paisagem e texto ampliado | Legibilidade e alcance dos controles |
| Rede lenta e interrompida | Feedback, recuperação e ausência de duplicações |
| Duas abas | Conflitos de resenha e ranking |
| Sessão persistente | Fechar/reabrir navegador, reiniciar app e recuperar senha |
| Desktop | Preservação das funções existentes |

### Fluxos obrigatórios

1. Criar conta, confirmar e-mail e completar perfil com CPF e avatar.
2. Entrar com e sem “Manter conectado”, sair e recuperar senha.
3. Buscar filme, série e anime, abrir detalhes e voltar.
4. Marcar “Vou ver”, avaliar e salvar primeira resenha.
5. Marcar “Vi novamente”, confirmar uma sessão e conferir o diário.
6. Editar resenha, perfil, sessão e lista.
7. Organizar ranking por toque e confirmar persistência após recarregar.
8. Repetir as gravações críticas após falha simulada de rede.

Executar build e os testes de integração existentes. Acrescentar testes de regressão para comportamentos alterados que ainda não estejam cobertos, principalmente ordem do ranking, sessão automática e persistência do login. Registrar as limitações dos dispositivos efetivamente testados.

**Aceite de publicação:** nenhum problema P0 pendente, fluxos obrigatórios aprovados, versão de teste servida em HTTPS e procedimento de atualização e recuperação documentado para o ambiente escolhido.

## Etapa 7 — PWA

- Definir nome, ícones, cores e manifesto de instalação.
- Validar abertura instalada e navegação no Android e no iPhone usados nos testes.
- Criar uma tela offline clara e uma política de atualização da aplicação.
- Começar pelo cache de recursos estáticos públicos. Respostas autenticadas exigem uma política explícita antes de qualquer cache persistente.
- Verificar logout e troca de usuário para evitar exibição de dados da sessão anterior.
- Definir separadamente se haverá leitura ou edição offline. O primeiro pacote de PWA pode oferecer instalação e uma tela de indisponibilidade de rede.

**Aceite:** instalação validada nos aparelhos disponíveis, abertura correta, atualização previsível e comportamento offline documentado.

## Ordem de implementação recomendada

Começar pelo diagnóstico visual e pelo ranking, que concentra as maiores limitações de toque. Depois adaptar estrelas, ações da obra e formulários. Finalizar com a matriz de aparelhos, correções de desempenho medidas e publicação do alpha web. A instalação como PWA vem após essa validação.

## Checklist da entrega

### Validação de 17/11/2026

- Chrome com emulação de toque, usando conta e dados de teste. Catálogo, perfil, ranking e detalhes de filme e série não apresentaram rolagem horizontal da página em 320, 360, 390 e 430 px, nem em paisagem a 844 × 390 px.
- Login concluído em viewport de 320 × 390 px. A altura reduzida ajuda a detectar conteúdo inacessível, mas a abertura do teclado virtual ainda precisa ser testada em aparelhos.
- Nota por episódio, categoria e ordem dos filmes mantidas após recarregar. A categoria cheia rolou horizontalmente e o botão de reordenação respondeu ao toque emulado.
- Os botões “Já assisti”, “Vou ver” e “Vi novamente” passaram a mostrar rótulos nas telas estreitas e em dispositivos com ponteiro grosso.

### Galaxy S24 — teste em aparelho real de 17/11/2026

- Chrome no Galaxy S24 com Android 17 e viewport de 360 px de largura. O teclado virtual estava visível no campo de senha, reduzindo a área visível a cerca de 284 px de altura. O login da conta fictícia foi concluído com toque no botão “Entrar”.
- O upload inicial usou um JPEG fictício pelo campo de arquivo. Na rodada seguinte, um PNG fictício foi selecionado pelo seletor nativo do Android no Chrome. A prévia mostrou a imagem esperada e o avatar permaneceu no perfil após salvar e recarregar.
- O gesto de deslize rolou uma categoria cheia do ranking. O toque na seta moveu o filme, e a nova ordem apareceu após recarregar.
- O teste usou a prévia isolada acessada por USB. O Safari no iPhone fica fora da rodada atual, conforme o escopo desta etapa.

### Prévia HTTPS e falha de rede

A versão de teste usa o Android como alvo desta rodada. A prévia HTTPS só expõe o frontend no IP privado escolhido e usa conta fictícia, serviços internos em loopback e bancos em memória. O certificado desta execução é local. Para liberar o alpha a convidados, use um ambiente com certificado reconhecido pelos navegadores.

Com os builds prontos e um certificado e chave PEM para o IP selecionado, execute:

```powershell
$env:SMOKE_PREVIEW='true'
$env:SMOKE_PREVIEW_HOST='<IP_PRIVADO_DO_COMPUTADOR>'
$env:SMOKE_TLS_CERT='data/mobile-preview-tls/preview.pem'
$env:SMOKE_TLS_KEY='data/mobile-preview-tls/key.pem'
$env:NODE_EXTRA_CA_CERTS=(Resolve-Path $env:SMOKE_TLS_CERT).Path
node scripts/smoke.mjs
```

O endereço HTTPS da prévia usa a porta 19443. O teste de integração verificou login, APIs autenticadas e controle de origem pelo HTTPS. No Chrome com viewport mobile, o envio foi interrompido antes de chegar ao servidor e, na tentativa seguinte, a resposta de uma gravação bem-sucedida foi descartada de propósito.

- Ranking: a ordem editada permaneceu disponível para salvar novamente. Após o reenvio, o ranking confirmou a gravação e a ordem persistiu após recarregar.
- Resenha: o texto ficou no formulário. O reenvio abriu o perfil e manteve apenas uma sessão no diário.

### Prévia isolada para aparelhos reais

Com os builds prontos, execute no PowerShell, substituindo o IP pelo endereço privado do computador na rede em que o celular estiver conectado.

```powershell
$env:SMOKE_PREVIEW='true'
$env:SMOKE_PREVIEW_HOST='<IP_PRIVADO_DO_COMPUTADOR>'
node scripts/smoke.mjs
```

Abra o endereço informado pelo script no Chrome do Android ou no Safari do iPhone. Use a conta fictícia gerada na execução. Apenas a interface fica disponível na rede local. A prévia usa HTTP, dados em memória e não envia e-mails. Encerre com Ctrl+C para descartar os dados.

1. Abra `/conta`, toque em E-mail e Senha e confira se consegue acessar “Entrar” com o teclado aberto.
2. No perfil, abra “Editar perfil”, escolha uma imagem sem dados pessoais, salve e confirme que o avatar aparece após recarregar.
3. Abra “Meu ranking”, deslize horizontalmente numa categoria cheia, altere a categoria de um filme e use as setas para reordenar. Recarregue para conferir a persistência.

Registre aparelho, navegador e resultado de cada passo. A validação de HTTPS, instalação e fluxos que dependem de contexto seguro fica para o ambiente de teste publicado.

- [ ] Diagnóstico visual concluído.
- [ ] Cabeçalho e formulários validados em telas estreitas.
- [ ] Ranking operável por toque e ordem persistente.
- [ ] Meias estrelas selecionáveis com precisão.
- [ ] Diário, listas e cards validados.
- [ ] Erros de rede e conflitos tratados.
- [ ] Sessão persistente verificada em celular.
- [ ] Android e iPhone testados, com evidências e limitações registradas.
- [ ] Alpha web mobile publicado em HTTPS.
- [ ] PWA implementada e validada na etapa posterior.
