package br.com.reportrpa;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@RestController
public class AwsBot {

    @GetMapping("/process")
    public void doProcess() {

        ChromeOptions options = new ChromeOptions();
        options.addArguments("start-maximized"); // <--- Abre maximizado

        // Inicia o navegador com as opções definidas
        WebDriver driver = new ChromeDriver(options);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        try {
            // Acessa a página de login
            driver.get("https://us-east-1.quicksight.aws.amazon.com/sn/auth/signin?redirect_uri=https%3A%2F%2Fus-east-1.quicksight.aws.amazon.com%2Fsn%2Fstart%3Fstate%3DhashArgs%2523%26isauthcode%3Dtrue");

            WebElement input = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("account-name-input")));
            input.sendKeys("insightcare");

            // Aguarde o botão "Próximo" e clique nele
            WebElement nextButton = wait.until(ExpectedConditions.elementToBeClickable(By.id("account-name-submit-button")));
            nextButton.click();

            WebElement username = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("username-input")));
            username.sendKeys("Informar login aqui");

            WebElement usernameNextButton = wait.until(ExpectedConditions.elementToBeClickable(By.id("username-submit-button")));
            usernameNextButton.click();

            WebElement passwordField = wait.until(
                    ExpectedConditions.visibilityOfElementLocated(By.id("awsui-input-0"))
            );
            passwordField.sendKeys("Informar senha aqui");

            // Aguarda o botão "Fazer login" ficar clicável e clica
            WebElement loginButton = wait.until(
                    ExpectedConditions.elementToBeClickable(
                            By.xpath("//button[@type='submit' and .//span[text()='Fazer login']]")
                    )
            );
            loginButton.click();

            // Espera o botão de fechar aparecer e clicar nele
            WebElement closeButton = wait.until(
                    ExpectedConditions.elementToBeClickable(
                            By.cssSelector("button.qs-close-btn.modal-close")
                    )
            );
            closeButton.click();

            WebElement analysisLink = wait.until(
                    ExpectedConditions.elementToBeClickable(
                            By.xpath("//a[contains(text(), 'duplicata-teste') and contains(@href, '/sn/analyses/')]")
                    )
            );
            analysisLink.click();

            WebElement botaoArquivo = wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.xpath("//button[@data-automation-id='file-menu-file']")
            ));

            wait.until(ExpectedConditions.invisibilityOfElementLocated(
                    By.cssSelector("div[aria-hidden='true'][style*='z-index: -1']"))
            );

            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", botaoArquivo);
            Thread.sleep(500);
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", botaoArquivo);

            // Aguarda o item "Exportar para PDF" e clica
            WebElement exportarPDF = wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.xpath("//li[@data-automation-id='file-menu-file-export-to-pdf']")
            ));

            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", exportarPDF);
            Thread.sleep(500);
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", exportarPDF);

            WebDriverWait longWait = new WebDriverWait(driver, Duration.ofSeconds(360));

            // Espera o Toastr aparecer dizendo que o PDF está pronto
            WebElement toastPDF = longWait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.xpath("//div[contains(@class, 'MuiAlert-message') and text()='Seu PDF está pronto.']")
            ));

            // Agora clica no botão "Baixar" dentro do Toast
            WebElement botaoBaixar = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//button[@data-automation-id='export_to_pdf_toast_download_now_button']")
            ));

            // Clica usando JavaScript (evita problemas com overlay)
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", botaoBaixar);

            Thread.sleep(20000);

            String downloadDir = System.getProperty("user.home") + "/Downloads";
            File dir = new File(downloadDir);

            Pattern pattern = Pattern.compile("Visão_Geral_\\d{4}-\\d{2}-\\d{2}T\\d{2}_\\d{2}_\\d{2}\\.pdf");

            Optional<File> pdfFile = Stream.of(Optional.ofNullable(dir.listFiles()).orElse(new File[0]))
                    .filter(file -> file.isFile() && pattern.matcher(file.getName()).matches())
                    .sorted((f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()))
                    .findFirst();

            if (pdfFile.isEmpty()) {
                System.err.println("Nenhum arquivo PDF correspondente encontrado.");
                return;
            }

            File fileToSend = pdfFile.get();
            System.out.println("Enviando: " + fileToSend.getName());

            String boundary = "----WebKitFormBoundary7MA4YWxkTrZu0gW";

            byte[] fileBytes = Files.readAllBytes(fileToSend.toPath());

            String partHeader = "--" + boundary + "\r\n" +
                    "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileToSend.getName() + "\"\r\n" +
                    "Content-Type: application/pdf\r\n\r\n";

            String partFooter = "\r\n--" + boundary + "--\r\n";

            // Combine todas as partes em um único corpo
            ByteBuffer bodyBuffer = ByteBuffer.allocate(
                    partHeader.getBytes().length + fileBytes.length + partFooter.getBytes().length
            );
            bodyBuffer.put(partHeader.getBytes());
            bodyBuffer.put(fileBytes);
            bodyBuffer.put(partFooter.getBytes());
            bodyBuffer.flip();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("colocar URL aqui"))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(bodyBuffer.array()))
                    .build();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("Resposta do servidor: " + response.statusCode());
            System.out.println("Body: " + response.body());

            driver.quit();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }
}
