# chatgpt-bot

Warning! Right now project is outdated!

The application allows to use a Telegram bot to redirect requests to ChatGpt.

An administrator has unlimited access and can control users who can also access this bot.

To start, you need to put the file with parameters - application.properties in the folder. 

Run the build by `mvn clean package` in root folder.

Then run the obtained file `my_app.jar` from `target` folder with the command `java -jar my_app.jar`.

**Mandatory** application properties:
- **openaiApiKey** - your API key for openai.com (i.e. "sk-21w1121...")
- **telegramBotToken** - your API key (token) for your telegram bot (i.e. "12345:Adsds...") 
- **telegramBotName** - bot username (i.e. "surkoff_chatgpt_bot")
- **openaiModelId** - openai model ID (i.e. "text-davinci-003")
- **maxTokens** - from openai.com (i.e. "2000")
- **adminChatId** - telegram admin chatId. You will use it to control access (i.e. "1234567")
- **openApiTimeoutS** - timeout in seconds to wait for response from openai (i.e. "150")
- **usersFile** - file to store users in CSV format (i.e. "users.csv")
