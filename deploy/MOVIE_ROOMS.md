# Escolher juntos

Acesso: `/salas`. Disponível no site, com gestos para smartphone e botões equivalentes para teclado e desktop. Nenhum bloqueio por dispositivo nesta alpha.

O anfitrião cria uma sala, compartilha o link e espera de 2 a 8 pessoas entrarem. Todos precisam de conta e perfil completos; o convite de sala não contorna a lista de cadastro da alpha. Ao entrar, o participante concorda em compartilhar os filmes do Vou ver com o grupo. Após todos votarem em um filme, se houver maioria absoluta, os nomes e avatares de quem quer ver são mostrados a quem passou, para uma única reconsideração.

Ao iniciar, a sala congela o grupo e mistura as listas Vou ver, remove duplicatas, filtra filmes (incluindo filmes de anime) e embaralha uma rodada de até 60 títulos. Para limitar chamadas ao catálogo, examina até 300 itens aleatórios das listas reunidas. Alterações posteriores no Vou ver não mudam a rodada. Sala sem filmes começa com sugestões do TMDB.

Os votos são persistidos e idempotentes por pessoa/filme. A segunda rodada exige mais da metade dos integrantes (2/3, 3/4, 3/5). Empate não dispara reconsideração. Manter o não encerra o pedido para aquela pessoa e filme, inclusive após recarregar. Match exige voto positivo de todos os participantes. O anfitrião confirma um dos matches como escolha final; isso encerra a votação. A operação não marca assistido nem altera as listas. Quando todos terminam, o anfitrião pode continuar com um lote de até 20 sugestões sem perder votos ou matches. Salas expiram após 7 dias. Limite de 10 salas não expiradas por anfitrião. Não há expulsão ou saída durante a rodada nesta versão; para trocar o grupo, crie outra sala.

Atualização da sala por polling a cada 4 segundos. As tabelas de sala, participantes, filmes e votos estão na migração V10 do serviço social e são incluídas no backup existente. Não há dependência de serviço pago de mensagens ou de WebSocket. Para um futuro app Android, reutilizar as regras no serviço social e adaptar a autenticação do cliente; não expor chaves internas no aplicativo.

## Sugestões

Meta por lote: 8 recentes (últimos 12 meses, mínimo de 30 votos), 8 bem avaliados (mínimo de 300 votos) e 4 populares (mínimo de 30 votos). Usamos duas páginas de candidatos por categoria; quotas faltantes são completadas com as demais categorias. Nota ponderada: `(votos * nota + 300 * 6.5) / (votos + 300)`. Dentro de cada categoria, não assistidos pelo grupo têm prioridade, com bônus de afinidade quando os gêneros estão disponíveis no cache de detalhes dos filmes curtidos. Excluímos adultos, títulos sem data, lançamentos futuros e filmes já presentes na sala. O cursor avança de duas em duas páginas; a sala comporta até 1000 títulos. Não há garantia de 20 inéditos em todo lote se o catálogo filtrado se esgotar.

## Filtro por gênero

Antes de iniciar, o anfitrião escolhe um ou mais gêneros, ou Todos os gêneros para limpar a seleção. Um filme passa no filtro quando pertence a qualquer um dos gêneros escolhidos (união). O filtro fica salvo na sala e vale tanto para os filmes das listas Vou ver quanto para os próximos lotes de sugestões. Os participantes podem consultar a seleção. Depois do início, o filtro permanece fixo para preservar a rodada e os votos; para outra seleção, crie uma nova sala. A migração V18 preserva o gênero das salas existentes como uma seleção de um item; salas sem filtro continuam aceitando todos os gêneros.

## Convites para amigos

No lobby, cada participante pode convidar amizades aceitas pelo painel Chame seus amigos. A lista é paginada em 50 pessoas e distingue amigos presentes e convidados. O convite fica disponível em Minhas salas do destinatário; ele precisa abrir e confirmar a entrada para compartilhar o Vou ver. Convites não reservam vagas nem adicionam membros automaticamente. O limite continua sendo 8 participantes.

A migração V19 armazena convites internos, únicos por sala e destinatário, com limite de 50 por sala. Apenas membros podem consultar seus próprios amigos e convidá-los, antes da votação e enquanto houver vaga. Convites de salas expiradas ou iniciadas não aparecem para quem ainda não entrou; excluir a sala remove os convites. Nenhum e-mail é enviado.
