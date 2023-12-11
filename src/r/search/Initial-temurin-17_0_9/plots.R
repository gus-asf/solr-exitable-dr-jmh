#
# Generate plots of the data. Plots will be printed in directory containing this script. Should be run in RStudio or
# RScript (not littler)
#

library(tidyverse)
library(TSstudio)
library(janitor)
library(stringr)
library(dplyr)

# TODO: find a porable way to import this rather than pasting it in every file.
getCurrentFileLocation <-  function()
{
  this_file <- commandArgs() %>%
    tibble::enframe(name = NULL) %>%
    tidyr::separate(col=value, into=c("key", "value"), sep="=", fill='right') %>%
    dplyr::filter(key == "--file") %>%
    dplyr::pull(value)
  if (length(this_file)==0)
  {
    this_file <- rstudioapi::getSourceEditorContext()$path
  }
  return(dirname(this_file))
}

fnames <- list.files(getCurrentFileLocation())

fils <- data.frame(fnames)

datafils <- fils %>% filter(grepl("^\\d{4}.*", fnames))

parsed <- strcapture(pattern = "(^\\d{4}.*T[^.]+)[^-]+-(\\d+)(.*)$",
                     x = datafils$fnames,
                     proto = list(date = character(),  warmRnds = integer(),foo = character()), perl=TRUE)

parsed <- strcapture(pattern = "(^\\d{4}.*T[^.]+)[^-]+-(\\d+)-(\\d+)-(null|true)-(?:.*-)?(\\w+)-(\\w+).*\\.txt$",
                     x = datafils$fnames,
                     proto = list(date = character(), warmRnds = integer(), testRnds=integer(), type=character(),feature=character(),stage=character()))

parsed["original"] = datafils

versus = "foo"

for (row in 1:nrow(parsed)) {
  type <- if (parsed[row, "type"] == "null") "standard" else "exitable"
  feature  <- parsed[row, "feature"]
  stage  <- parsed[row, "stage"]
  filname <- paste(type,feature,stage,sep="-")
  original <- parsed[row,"original"]

  tmp <- `standard-data` <- read.table(paste(getCurrentFileLocation(),original,sep="/"), quote="", comment.char="")
  if (stage =="data") {
    versus <- cbind( tmp$V1, as.data.frame(versus))
  }
  colnames(versus)[1] <- type
  ts_plotResult <- ts_plot(as.ts(tmp), title = paste0(str_replace(type, "^\\w{1}", toupper)," Directory Reader"), Xtitle = "Rounds", Ytitle = "Nanos")
  plotly::save_image(ts_plotResult,paste(getCurrentFileLocation(),paste0(filname,".png"), sep = "/"))
}
versus <- versus[,-3]

ts_plotResult <- ts_plot(as.ts(versus), title = "Standard Directory Reader vs Exitable Directory Reader", Xtitle = "Rounds", Ytitle = "Nanos")
plotly::save_image(ts_plotResult,paste(getCurrentFileLocation(),"standard_v_exitable.png", sep = "/"),width=2000,height=1500)