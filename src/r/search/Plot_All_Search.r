library(tidyverse)
library(TSstudio)
TMP <- read_csv("All_Data_Search.csv")
ts_plotResult <- ts_plot(as.ts(TMP), title = "Exitable Directory Comparison", Ytitle = "Nanos", Xtitle = "Round")
plotly::save_image(ts_plotResult, "all.png", width = 2400)

