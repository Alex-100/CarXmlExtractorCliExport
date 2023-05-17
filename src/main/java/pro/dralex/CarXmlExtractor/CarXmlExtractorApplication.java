package pro.dralex.CarXmlExtractor;

import jakarta.annotation.PostConstruct;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;
import me.tongfei.progressbar.ProgressBarStyle;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@SpringBootApplication
@Slf4j
@RequiredArgsConstructor
public class CarXmlExtractorApplication {

    public static void main(String[] args) {
        SpringApplication.run(CarXmlExtractorApplication.class, args);
    }

    @PostConstruct
    public void loader() {
        try {
            run();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private final CarModelRepo carModelRepo;
    private final CarMakeRepo carMakeRepo;
    private final ConcurrentHashMap<String, ProgressStatus> progressBars = new ConcurrentHashMap<>();

    @Data
    @AllArgsConstructor
    private class ProgressStatus {

        private Long bytesDownloaded;
        private Long bytesTotal;

        public boolean hasBytesTotal(){
            return bytesTotal != null;
        }
        public boolean isFinished(){
            return bytesDownloaded >= bytesTotal;
        }
    }

    public void run() throws IOException, InterruptedException {
        final String avFileName = "av-style.xml";
        final String auFilename = "au-style.xml";
        final String drFileName = "dr-style.xml";

        loadFiles(avFileName, auFilename, drFileName);

        List<Car> avCars = getAvCarsFromXml(avFileName);
        List<Car> auCars = getAuCarsFromXml(auFilename);
        List<Car> drCars = getDrCarsFromXml(drFileName);
        List<CarMakeConnector> manualMakes = getManualRulesForMakes();
        List<CarMakeConnector> supportedMakes = showSupportedMakes(avCars, auCars, drCars, manualMakes);
        carMakeRepo.saveAll(supportedMakes);

        List<CarModelConnector> rules = getManualRulesForModels();
        List<CarModelConnector> supportedModels = getSupportedModels(avCars, auCars, drCars, supportedMakes, rules);
        carModelRepo.saveAll(supportedModels);

        deleteFile(avFileName);
        deleteFile(auFilename);
        deleteFile(drFileName);
    }

    private void deleteFile(String fileName) {
        File file = new File(fileName);
        if (!file.delete()) {
            System.out.println("Cannot delete file:" + fileName);
        }
    }

    private void loadFiles(String avFileName, String auFilename, String drFileName) throws IOException, InterruptedException {

        final String auStyleUrl = "https://path/to/file/one.xml";
        final String avStyleUrl = "https://path/to/file/two.xml";
        final String drStyleUrl = "https://path/to/file/three.xml";

        ExecutorService executorService = Executors.newFixedThreadPool(3);
        Downloader task1 = new Downloader(new URL(auStyleUrl), auFilename);
        Downloader task2 = new Downloader(new URL(avStyleUrl), avFileName);
        Downloader task3 = new Downloader(new URL(drStyleUrl), drFileName);
        executorService.execute(task1);
        executorService.execute(task2);
        executorService.execute(task3);

        //..await data collection
        while (progressBars.values().stream().filter(ProgressStatus::hasBytesTotal).count() != 3) {
            TimeUnit.SECONDS.sleep(1);
        }
        ProgressBarBuilder pbb = new ProgressBarBuilder()
                .setStyle(ProgressBarStyle.ASCII)
                .setTaskName(avFileName)
                .setInitialMax(progressBars.get(avFileName).getBytesTotal())
                .setSpeedUnit(ChronoUnit.SECONDS)
                .setUnit("Kb", 1024)
                .showSpeed();
        try (
                ProgressBar pb1 = pbb.setInitialMax(progressBars.get(avFileName).getBytesTotal()).build();
                ProgressBar pb2 = pbb.setInitialMax(progressBars.get(auFilename).getBytesTotal()).build();
                ProgressBar pb3 = pbb.setInitialMax(progressBars.get(drStyleUrl).getBytesTotal()).build()
        ) {
            System.out.println();
            //...show a download progress
            while (progressBars.values().stream().filter(ProgressStatus::isFinished).count() != 3) {
                pb1.stepTo(progressBars.get(avFileName).getBytesDownloaded());
                pb2.stepTo(progressBars.get(auFilename).getBytesDownloaded());
                pb3.stepTo(progressBars.get(drFileName).getBytesDownloaded());
                TimeUnit.SECONDS.sleep(1);
            }
        }
        System.out.println();
    }


    private class Downloader implements Runnable {
        private final URL url;
        private final String fileName;

        public Downloader(URL url, String fileName) {
            this.url = url;
            this.fileName = fileName;
        }

        private long getUrlFileSize() throws IOException {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                return conn.getContentLengthLong();
            }  finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }

        private void getFile(long fileSize) throws IOException, InterruptedException {
            final BufferedInputStream in = new BufferedInputStream(url.openStream());
            final FileOutputStream out = new FileOutputStream(fileName);
            final byte[] dataBuffer = new byte[1024];
            int bytesRead;
            long totalBytes = 0;
            while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                totalBytes += 1024;
                progressBars.put(fileName, new ProgressStatus(totalBytes, fileSize));
                out.write(dataBuffer, 0, bytesRead);
            }
            in.close();
            out.close();
        }

        @Override
        public void run() {
            try {
                long fileSize = getUrlFileSize();
                getFile(fileSize);
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private List<CarModelConnector> getManualRulesForModels() {
        List<CarModelConnector> result = new ArrayList<>();
        // ...
        return result;
    }

    private List<CarMakeConnector> getManualRulesForMakes() {
        List<CarMakeConnector> result = new ArrayList<>();
        //...
        return result;
    }

    private List<CarModelConnector> getSupportedModels(List<Car> avCars,
                                                       List<Car> auCars,
                                                       List<Car> drCars,
                                                       List<CarMakeConnector> supportedMakes,
                                                       List<CarModelConnector> rulesManual) {

        List<CarModelConnector> result = new ArrayList<>();
        System.out.println();
        System.out.println(">>>>> Model difference");
        for (CarMakeConnector make : supportedMakes) {
            System.out.println();
            System.out.println("-- [" + make.getAvStyle().toUpperCase() + "] --");
            System.out.printf("| %1$26s | %2$26s | %3$26s | %4$26s |", "[MODEL GROUP]", "[AV-STYLE]", "[AU-STYLE]", "[DR-STYLE]");
            System.out.println();
            List<String> avModels = new ArrayList<>(avCars.stream()
                    .filter(car -> car.getMake().equalsIgnoreCase(make.getAvStyle()))
                    .map(Car::getModel)
                    .distinct()
                    .sorted()
                    .toList());

            List<String> auModels = new ArrayList<>(auCars.stream()
                    .filter(car -> car.getMake().equalsIgnoreCase(make.getAuStyle()))
                    .map(Car::getModel)
                    .sorted()
                    .distinct()
                    .toList());

            List<String> drModels = new ArrayList<>(drCars.stream()
                    .filter(car -> car.getMake().equalsIgnoreCase(make.getDrStyle()))
                    .map(Car::getModel)
                    .sorted()
                    .distinct()
                    .toList());

            List<String> commonModels = avCars.stream()
                    .filter(car -> auModels.contains(car.getModel()) && drModels.contains(car.getModel()))
                    .map(Car::getModel)
                    .sorted()
                    .distinct()
                    .toList();

            System.out.println(">> common");
            commonModels.forEach(model -> {
                result.add(new CarModelConnector(make.getAvStyle(), model, model, model));
                System.out.printf("| %1$26s | %2$26s | %3$26s | %4$26s |", model, model, model, model);
                System.out.println();
                avModels.remove(model);
                auModels.remove(model);
                drModels.remove(model);
            });


            List<CarModelConnector> rulesAutomatic = new ArrayList<>();
            List<String> avModelsTmp = new ArrayList<>(avModels);
            avModelsTmp.forEach(avModelTmp -> {
                        Optional<String> auModel = auModels.stream().filter(item -> item.equalsIgnoreCase(avModelTmp)).findFirst();
                        Optional<String> drModel = drModels.stream().filter(item -> item.equalsIgnoreCase(avModelTmp)).findFirst();
                        Optional<String> avModel = avModels.stream().filter(item -> item.equalsIgnoreCase(avModelTmp)).findFirst();
                        if (auModel.isPresent() && drModel.isPresent() && avModel.isPresent()) {
                            result.add(new CarModelConnector(make.getAvStyle(), avModel.get(), auModel.get(), drModel.get()));
                            rulesAutomatic.add(new CarModelConnector(make.getAvStyle(), avModel.get(), auModel.get(), drModel.get()));
                            avModels.remove(avModel.get());
                            auModels.remove(auModel.get());
                            drModels.remove(drModel.get());
                        }
                    }
            );
            rulesAutomatic.forEach(model -> {
                System.out.printf(
                        "| %1$26s | %2$26s | %3$26s | %4$26s | [rule auto] ",
                        model.getModel(),
                        model.getAvStyle(),
                        model.getAuStyle(),
                        model.getDrStyle()
                );
                System.out.println();
            });

            rulesManual.forEach(rule -> {
                if (rule.getMake().equalsIgnoreCase(make.getAvStyle())) {
                    System.out.printf(
                            "| %1$26s | %2$26s | %3$26s | %4$26s | [rule manual]  ",
                            rule.getModel(),
                            rule.getAvStyle(),
                            rule.getAuStyle(),
                            rule.getDrStyle()
                    );

                    System.out.println();
                    result.add(new CarModelConnector(make.getAvStyle(), rule.getAvStyle(), rule.getAvStyle(), rule.getAuStyle(), rule.getDrStyle()));
                    avModels.remove(rule.getAvStyle());
                    auModels.remove(rule.getAuStyle());
                    drModels.remove(rule.getDrStyle());
                }
            });


            System.out.println(">> difference");
            int maxIndex = Collections.max(List.of(avModels.size(), auModels.size(), drModels.size()));
            for (int i = 0; i < maxIndex; i++) {
                String avModel = "";
                String auModel = "";
                String drModel = "";
                if (i <= avModels.size() - 1) {
                    avModel = avModels.get(i);
                }
                if (i <= auModels.size() - 1) {
                    auModel = auModels.get(i);
                }
                if (i <= drModels.size() - 1) {
                    drModel = drModels.get(i);
                }
                System.out.printf("| %1$26s | %2$26s | %3$26s | %4$26s |","", avModel, auModel, drModel);
                System.out.println();
            }

        }
        return result;
    }

    @SafeVarargs
    private <T> List<T> findIntersection(List<T>... lists) {
        if (lists.length > 1) {
            List<T> intersection = Arrays.stream(lists).findFirst().get();
            for (List<T> list : lists) {
                intersection = CollectionUtils.intersection(intersection, list).stream().toList();
            }
            return intersection;
        }
        return List.of();
    }

    private List<CarMakeConnector> showSupportedMakes(List<Car> avStyleCars,
                                                      List<Car> auStyleCars,
                                                      List<Car> drStyleCars,
                                                      List<CarMakeConnector> rulesManual) {

        List<String> avMakeList = new ArrayList<>(avStyleCars.stream()
                .map(Car::getMake)
                .distinct()
                .sorted()
                .toList());

        List<String> auMakeList = new ArrayList<>(auStyleCars.stream()
                .map(Car::getMake)
                .distinct()
                .sorted()
                .toList());

        List<String> drMakeList = new ArrayList<>(drStyleCars.stream()
                .map(Car::getMake)
                .distinct()
                .sorted()
                .toList());


        System.out.println(">>>>> Supported MAKES");
        System.out.printf("| %1$26s | %2$26s | %3$26s |", "[AV-STYLE]", "[AU-STYLE]", "[DR-STYLE]");
        System.out.println();
        List<CarMakeConnector> supportedMakes = new ArrayList<>();
        List<String> makes = findIntersection(avMakeList, auMakeList, drMakeList);
        makes.stream().sorted().forEach(make -> {
            System.out.printf("| %1$26s | %2$26s | %3$26s |", make, make, make);
            System.out.println();
            supportedMakes.add(new CarMakeConnector(make, make, make));
            avMakeList.remove(make);
            auMakeList.remove(make);
            drMakeList.remove(make);
        });


        List<CarMakeConnector> rulesAutomatic = new ArrayList<>();
        List<String> avModelsTmp = new ArrayList<>(avMakeList);
        avModelsTmp.forEach(avModelTmp -> {
                    Optional<String> auModel = auMakeList.stream().filter(item -> item.equalsIgnoreCase(avModelTmp)).findFirst();
                    Optional<String> drModel = drMakeList.stream().filter(item -> item.equalsIgnoreCase(avModelTmp)).findFirst();
                    if (auModel.isPresent()
                            && drModel.isPresent()
//							&& auModel.get().equalsIgnoreCase(avModelTmp)
//							&& drModel.get().equalsIgnoreCase(avModelTmp)
                    ) {
                        rulesAutomatic.add(new CarMakeConnector(avModelTmp, auModel.get(), drModel.get()));
                        avMakeList.remove(avModelTmp);
                        auMakeList.remove(auModel.get());
                        drMakeList.remove(drModel.get());
                    }
                }
        );

        rulesAutomatic.forEach(model -> {
            System.out.printf("| %1$26s | %2$26s | %3$26s | [rule automatic] ", model.getAvStyle(), model.getAuStyle(), model.getDrStyle());
            System.out.println();
            supportedMakes.add(new CarMakeConnector(model.getAvStyle(), model.getAuStyle(), model.getDrStyle()));

        });

        rulesManual.forEach(rule -> {
            System.out.printf("| %1$26s | %2$26s | %3$26s | [rule manual] ", rule.getAvStyle(), rule.getAuStyle(), rule.getDrStyle());
            System.out.println();
            avMakeList.remove(rule.getAvStyle());
            auMakeList.remove(rule.getAuStyle());
            drMakeList.remove(rule.getDrStyle());
            supportedMakes.add(new CarMakeConnector(rule.getAvStyle(), rule.getAuStyle(), rule.getDrStyle()));
        });


        System.out.println(">>>>> Unsupported MAKES");

        int maxIndex = Collections.max(List.of(avMakeList.size(), auMakeList.size(), drMakeList.size()));
        for (int i = 0; i < maxIndex; i++) {
            String avMake = "";
            String auMake = "";
            String drMake = "";
            if (i <= avMakeList.size() - 1) {
                avMake = avMakeList.get(i);
            }
            if (i <= auMakeList.size() - 1) {
                auMake = auMakeList.get(i);
            }
            if (i <= drMakeList.size() - 1) {
                drMake = drMakeList.get(i);
            }
            System.out.printf("| %1$26s | %2$26s | %3$26s |", auMake, avMake, drMake);
            System.out.println();
        }

        return supportedMakes;

    }


    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private class DrStyleCarMake {
        private int id;
        private String make;
    }

    private List<Car> getDrCarsFromXml(String fileName) {
        List<Car> result = new ArrayList<>();
        try {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = builder.parse(new File(fileName));
            doc.getDocumentElement().normalize();

            XPath xPath = XPathFactory.newInstance().newXPath();
            NodeList makesList = (NodeList) xPath.compile("/References/Marks/Mark").evaluate(doc, XPathConstants.NODESET);
            log.info("dr-style total models size:{}", makesList.getLength());
            List<DrStyleCarMake> drStyleCarMakes = new ArrayList<>();
            for (int i = 0; i <= makesList.getLength() - 1; i++) {
                Element elementMark = (Element) makesList.item(i);
                String idMark = elementMark.getElementsByTagName("idMark").item(0).getTextContent();
                String make = elementMark.getElementsByTagName("sMark").item(0).getTextContent();
                drStyleCarMakes.add(new DrStyleCarMake(Integer.parseInt(idMark), make));
            }

            NodeList modelsList = (NodeList) xPath.compile("/References/Models/Model").evaluate(doc, XPathConstants.NODESET);
            for (int i = 0; i <= modelsList.getLength() - 1; i++) {
                Element elementMark = (Element) modelsList.item(i);
                int makeId = Integer.parseInt(elementMark.getElementsByTagName("idMark").item(0).getTextContent());
                String model = elementMark.getElementsByTagName("sModel").item(0).getTextContent();
                String make = drStyleCarMakes.stream()
                        .filter(car -> car.id == makeId)
                        .findFirst()
                        .orElse(new DrStyleCarMake(-1, ""))
                        .getMake();
                result.add(new Car(make, model));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    public List<Car> getAvCarsFromXml(String fileName) {
        List<Car> result = new ArrayList<>();
        try {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = builder.parse(new File(fileName));
            doc.getDocumentElement().normalize();

            XPath xPath = XPathFactory.newInstance().newXPath();
            NodeList nodeList = (NodeList) xPath.compile("/Catalog/Make/Model").evaluate(doc, XPathConstants.NODESET);
            log.info("av-style total model size:{}", nodeList.getLength());
            log.info("");
            for (int i = 0; i <= nodeList.getLength() - 1; i++) {
                Node model = nodeList.item(i);
                NamedNodeMap modelDetail = model.getAttributes();
                Node make = model.getParentNode();
                NamedNodeMap makeDetail = make.getAttributes();
                String modelStr = modelDetail.getNamedItem("name").getNodeValue();
                String makeStr = makeDetail.getNamedItem("name").getNodeValue();
                result.add(new Car(makeStr, modelStr));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    public List<Car> getAuCarsFromXml(String fileName) {
        List<Car> result = new ArrayList<>();
        try {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = builder.parse(new File(fileName));
            doc.getDocumentElement().normalize();

            XPath xPath = XPathFactory.newInstance().newXPath();
            NodeList nodeList = (NodeList) xPath.compile("/catalog/mark/folder").evaluate(doc, XPathConstants.NODESET);
            log.info("au-style total model size:{}", nodeList.getLength());
            log.info("");
            for (int i = 0; i <= nodeList.getLength() - 1; i++) {
                Node model = nodeList.item(i);
                NamedNodeMap modelDetail = model.getAttributes();
                Node make = model.getParentNode();
                NamedNodeMap makeDetail = make.getAttributes();
                String modelStr = modelDetail.getNamedItem("name").getNodeValue().replaceAll(",.+", "");
                String makeStr = makeDetail.getNamedItem("name").getNodeValue();
                result.add(new Car(makeStr, modelStr));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }


}
