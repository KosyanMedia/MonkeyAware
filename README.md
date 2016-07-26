Утилита проверяет список IP, подсетей и доменных имён на наличие блокировки в РосКомНадзоре.

Особенности работы:
* cписок заблокированных сервисов подгружается отсюда: https://reestr.rublacklist.net/api/current
* список IP и доменных имён берётся из CVS файла в формате выгрузки из https://docs.google.com/spreadsheets/d/16d-pm9F-N4J0Ctih7WLO9i1D0s-aBrEPvNtDMioMuQ8/edit
* по всем доменным именам берутся A записи из DNS Яндекса и Google, программа так же проверит эти IP на блокировку

Сборка -
```
mvn clean compile package install
```

Запуск -
``` 
java "target/monkey-aware-1.0-jar-with-dependencies.jar" com.jetradar.monkeyaware.MonkeyAware mode=[trigger|report] path/to/file.csv
```


В mode=trigger выдаёт количество найденых блокировок. В норме - 0.
В mode=report выдаёт репорт по найденым блокировкам.