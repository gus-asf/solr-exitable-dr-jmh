#
# Generate plots of the data. Plots will be printed in directory containing this script.
#

library(tidyverse)
library(TSstudio)

`standard-data` <- read.table("~/projects/gus-asf/solr/code/solr-exitable-dr-jmh/src/r/search/Initial-azul-11_0_19/2023-12-04T00:58:07.543832Z-1000-1000-null-data.txt", quote="", comment.char="")
`standard-warming` <- read.table("~/projects/gus-asf/solr/code/solr-exitable-dr-jmh/src/r/search/Initial-azul-11_0_19/2023-12-04T00:58:07.543832Z-1000-1000-null-warming.txt", quote="", comment.char="")
`exitable-data` <- read.table("~/projects/gus-asf/solr/code/solr-exitable-dr-jmh/src/r/search/Initial-azul-11_0_19/2023-12-04T01:31:28.004846Z-1000-1000-true-data.txt", quote="", comment.char="")
`exitable-warming` <- read.table("~/projects/gus-asf/solr/code/solr-exitable-dr-jmh/src/r/search/Initial-azul-11_0_19/2023-12-04T01:31:28.004846Z-1000-1000-true-warming.txt", quote="", comment.char="")

ts_plotResult <- ts_plot(as.ts(`standard-data`), title = "Standard Directory Reader", Xtitle = "Rounds", Ytitle = "Nanos")
plotly::save_image(ts_plotResult,paste(getCurrentFileLocation(),"standard-data.png", sep = "/"))
ts_plotResult <- ts_plot(as.ts(`standard-warming`), title = "Standard Directory Reader", Xtitle = "Rounds", Ytitle = "Nanos")
plotly::save_image(ts_plotResult,paste(getCurrentFileLocation(),"standard-warming.png", sep = "/"))
ts_plotResult <- ts_plot(as.ts(`exitable-data`), title = "Standard Directory Reader", Xtitle = "Rounds", Ytitle = "Nanos")
plotly::save_image(ts_plotResult,paste(getCurrentFileLocation(),"exitable-data.png", sep = "/"))
ts_plotResult <- ts_plot(as.ts(`exitable-warming`), title = "Standard Directory Reader", Xtitle = "Rounds", Ytitle = "Nanos")
plotly::save_image(ts_plotResult,paste(getCurrentFileLocation(),"exitable-warming.png", sep = "/"))


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