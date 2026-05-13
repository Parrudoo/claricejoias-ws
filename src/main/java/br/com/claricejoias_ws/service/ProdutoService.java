package br.com.claricejoias_ws.service;

import br.com.claricejoias_ws.dto.ProdutoDTO;
import br.com.claricejoias_ws.model.Produto;
import br.com.claricejoias_ws.repository.ProdutoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.Converters;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.w3c.dom.Element;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProdutoService {

    private final ProdutoRepository produtoRepository;
    private final AutenticacaoService autenticacaoService;
    private final ModelMapper mapper;
    private final MinioService minioService;

    public List<ProdutoDTO> listarTodos() {
        return produtoRepository.findAll().stream()
                .map(p -> mapper.map(p, ProdutoDTO.class))
                .toList();
    }

    public Optional<Produto> buscarPorId(Long id) {
        return produtoRepository.findById(id);
    }

    // Recebe o arquivo junto com o produto
    public Produto salvar(Produto produto, List<MultipartFile> files) throws Exception {

        // Verifica se a lista de arquivos não é nula e não está vazia
        if (files != null && !files.isEmpty()) {

            // Cria uma lista vazia para guardar as URLs/Paths que o MinIO vai devolver
            List<String> caminhosImagens = new ArrayList<>();

            // Passa por cada arquivo recebido do React
            for (MultipartFile file : files) {
                // Se o arquivo não estiver vazio (garantia extra)
                if (!file.isEmpty()) {
                    // Faz o upload de UM arquivo por vez
                    String objectName = minioService.upload(file);
                    // Adiciona o caminho retornado na nossa lista
                    caminhosImagens.add(objectName);
                }
            }

            // Em vez de setPathImg, agora você precisa setar uma lista
            produto.setLoginUsuario(autenticacaoService.getUsername());
            produto.setRascunho(false);
            produto.setImagens(caminhosImagens);
        }

        return produtoRepository.save(produto);
    }

    public Produto atualizar(Long id, Produto produtoAtualizado, List<MultipartFile> files) throws Exception {
        return produtoRepository.findById(id).map(produto -> {
            produto.setNome(produtoAtualizado.getNome());
            produto.setPreco(produtoAtualizado.getPreco());
            produto.setPrecoCusto(produtoAtualizado.getPrecoCusto());
            produto.setCodigo(produtoAtualizado.getCodigo());
            produto.setMaterial(produtoAtualizado.getMaterial());
            produto.setEstoque(produtoAtualizado.getEstoque());
            produto.setLoginUsuario(autenticacaoService.getUsername());
            produto.setSubcategoria(produtoAtualizado.getSubcategoria());
            produto.setRascunho(false);

            // Se o usuário enviou arquivos novos na hora de editar
            if (files != null && !files.isEmpty()) {
                try {
                    // 1. Deleta as imagens antigas do MinIO para não ocupar espaço à toa
                    if (produto.getImagens() != null && !produto.getImagens().isEmpty()) {
                        for (String imagemAntiga : produto.getImagens()) {
                            minioService.delete(imagemAntiga);
                        }
                        // Limpa a lista velha do banco
                        produto.getImagens().clear();
                    }

                    // 2. Faz o upload das imagens novas
                    List<String> novasImagens = new ArrayList<>();
                    for (MultipartFile file : files) {
                        if (!file.isEmpty()) {
                            String objectName = minioService.upload(file);
                            novasImagens.add(objectName);
                        }
                    }

                    // 3. Salva a nova lista de links no produto
                    produto.setImagens(novasImagens);

                } catch (Exception e) {
                    throw new RuntimeException("Erro ao atualizar as imagens da joia", e);
                }
            }

            return produtoRepository.save(produto);
        }).orElseThrow(() -> new RuntimeException("Produto não encontrado com o ID: " + id));
    }


    public void deletar(Long id) {
        produtoRepository.findById(id).ifPresent(produtoRepository::delete);
    }

    @Transactional
    public void processarXmlNfe(MultipartFile file) throws Exception {
        // Configura o parser XML nativo do Java
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(file.getInputStream());
        document.getDocumentElement().normalize();

        // Pega todos os itens da nota (tag <det>)
        NodeList nList = document.getElementsByTagName("det");

        for (int i = 0; i < nList.getLength(); i++) {
            Node node = nList.item(i);

            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;

                // Extrai as tags brutas
                String xProdRaw = getTagValue("xProd", element);
                String vUnComStr = getTagValue("vUnCom", element);

                String codigoExtraido = "";
                String descricaoExtraida = "";

                // Lógica para separar o código da descrição baseada no hífen
                if (xProdRaw != null && !xProdRaw.trim().isEmpty()) {
                    int indexHifen = xProdRaw.indexOf("-");

                    if (indexHifen != -1) {
                        // Pega tudo do começo até o hífen e remove os espaços
                        codigoExtraido = xProdRaw.substring(0, indexHifen).trim();

                        // Pega tudo depois do hífen até o final e remove os espaços
                        descricaoExtraida = xProdRaw.substring(indexHifen + 1).trim();
                    } else {
                        // Fallback: se por acaso vier algum produto sem o hífen,
                        // tenta pegar da tag <cProd> normal e usa o xProd inteiro como nome
                        codigoExtraido = getTagValue("cProd", element);
                        descricaoExtraida = xProdRaw.trim();
                    }
                }

                // Só salvamos se o código extraído for válido
                if (codigoExtraido != null && !codigoExtraido.isEmpty()) {

                    // Verifica se já existe para não duplicar usando o novo código
                    Optional<Produto> existente = produtoRepository.findByCodigo(codigoExtraido);

                    if (existente.isEmpty()) {
                        Produto rascunho = new Produto();
                        rascunho.setCodigo(codigoExtraido); // Ex: "P710"
                        rascunho.setNome(descricaoExtraida); // Ex: "Pulseira Folheado a Ouro"

                        // 👇 NOVA LÓGICA DE PRECIFICAÇÃO INTEGRADA AQUI
                        if (vUnComStr != null && !vUnComStr.trim().isEmpty()) {
                            BigDecimal custo = new BigDecimal(vUnComStr);
                            rascunho.setPrecoCusto(custo);

                            BigDecimal precoVenda;

                            // Se o custo for menor que R$ 20,00, multiplica por 3
                            if (custo.compareTo(new BigDecimal("20.00")) < 0) {
                                precoVenda = custo.multiply(new BigDecimal("3.0"));
                            }
                            // Se o custo for entre R$ 20,00 e R$ 100,00, multiplica por 2.5
                            else if (custo.compareTo(new BigDecimal("100.00")) <= 0) {
                                precoVenda = custo.multiply(new BigDecimal("2.5"));
                            }
                            // Se for maior que R$ 100,00, multiplica por 2
                            else {
                                precoVenda = custo.multiply(new BigDecimal("2.0"));
                            }

                            // Define o preço de venda arredondando para 2 casas decimais
                            rascunho.setPreco(precoVenda.setScale(2, RoundingMode.HALF_UP));
                        }

                        // Define como inativo e sem estoque até a Clarice revisar e salvar a foto
                        rascunho.setEstoque(0);

                        produtoRepository.save(rascunho);
                    }
                }
            }
        }
    }


    // Método auxiliar para buscar o texto dentro da tag com segurança
    private String getTagValue(String tag, org.w3c.dom.Element element) {
        NodeList nodeList = element.getElementsByTagName(tag);
        if (nodeList != null && nodeList.getLength() > 0) {
            Node node = nodeList.item(0);
            if (node != null) {
                return node.getTextContent();
            }
        }
        return null;
    }

    public Optional<ProdutoDTO> buscarPorCodigo(String codigo) {
        Optional<ProdutoDTO> produto = produtoRepository.findByCodigo(codigo)
                .map(p -> mapper.map(p, ProdutoDTO.class));
        return produto;
    }

    public List<ProdutoDTO> listarRascunhos() {
        List<ProdutoDTO> produtos = produtoRepository.findByRascunhoTrue().stream().map(p -> mapper.map(p,ProdutoDTO.class)).collect(Collectors.toList());
        return produtos;
    }


    public void processarImagensEmMassa(List<MultipartFile> imagensEnviadas) {

        for (MultipartFile imagem : imagensEnviadas) {
            String nomeOriginal = imagem.getOriginalFilename();

            if (nomeOriginal == null || nomeOriginal.isEmpty()) {
                continue; // Pula se o arquivo for inválido
            }

            // 1. Extrai o código base (Ex: "p710_g.jpg" -> "P710")
            String codigoProduto = extrairCodigoDoArquivo(nomeOriginal);

            // 2. Busca o produto no banco
            Optional<Produto> produtoOpt = produtoRepository.findByCodigoIgnoreCase(codigoProduto);

            if (produtoOpt.isPresent()) {
                Produto produto = produtoOpt.get();

                try {
                    // 3. Faz o upload para o Minio
                    String objectNameSalvo = minioService.upload(imagem);

                    // 4. Verifica se a imagem JÁ ESTÁ na lista do banco de dados
                    boolean imagemJaExisteNoBanco = produto.getImagens() != null && produto.getImagens().contains(objectNameSalvo);

                    if (!imagemJaExisteNoBanco) {
                        // Se NÃO existe no banco, adiciona na lista e salva
                        produto.adicionarImagem(objectNameSalvo);
                        produtoRepository.save(produto);

                        log.info("Sucesso: Nova imagem '{}' adicionada ao produto '{}' e salva no Minio.",
                                objectNameSalvo, codigoProduto);
                    } else {
                        log.info("Atualização: A foto '{}' do produto '{}' foi substituída no Minio com sucesso.",
                                objectNameSalvo, codigoProduto);
                    }

                } catch (Exception e) {
                    log.error("Erro ao enviar a imagem '{}' para o Minio: {}", nomeOriginal, e.getMessage());
                }

            } else {
                log.warn("Aviso: Produto '{}' não encontrado para a imagem '{}'", codigoProduto, nomeOriginal);
            }
        }
    }

    private String extrairCodigoDoArquivo(String nomeArquivo) {
        // 1. Remove a extensão (ex: .jpg, .png, .jpeg)
        int indexPonto = nomeArquivo.lastIndexOf('.');
        String nomeSemExtensao = (indexPonto != -1) ? nomeArquivo.substring(0, indexPonto) : nomeArquivo;

        // 2. Procura se existe um underline "_" (ex: p710_g)
        int indexUnderline = nomeSemExtensao.indexOf('_');

        // 3. Se houver underline, pega apenas a parte ANTES dele. Se não houver, mantém o nome.
        String codigo = (indexUnderline != -1) ? nomeSemExtensao.substring(0, indexUnderline) : nomeSemExtensao;

        // 4. Retorna tudo em maiúsculo e garante que não fiquem espaços sobrando
        return codigo.trim().toUpperCase();
    }

}