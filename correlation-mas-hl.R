# Runs Mann-Whitney test and Kendall Correlation test between architecture-issues and code-issues
# Saves results in "correlations.csv".
#
# To run script from command line:
# "C:\Program Files\R\R-3.3.3\bin\x64\Rscript.exe" correlations.R
#
#
# Mann-Whitney test:
#      p < 0.05  -->
# Shapiro-Wilkinson Normality test:
#      p > 0.05  -->  normal distribution      -->  use Pearson correlation
#      p < 0.05  -->  not normal distribution  -->  use Kendall correlation
# Kendall correlation:
#      p < 0.05  -->
# Pearson correlation:
#      p < 0.05  -->

data <- read.csv("mas-issues-by-package.csv", header=T)
issueName <- c()
mannWhitneyPvalue <- c()
shapiroWilkPvalue <- c()
kendallPvalue <- c()
kendallTau <- c()
pearsonPvalue <- c()
pearsonCor <- c()
for(i in 6:dim(data)[2]) {
 issueName <- c(issueName, colnames(data)[i])
 mat <- matrix(c(data$hl, data[,i]),nrow=length(data$hl))
 mannWhitneyPvalue <- c(mannWhitneyPvalue, wilcox.test(mat[,1],mat[,2])$p.value)
 shapiroWilkPvalue <- tryCatch({
  c(shapiroWilkPvalue, shapiro.test(mat[,2])$p.value) },
  error = function(e) { c(shapiroWilkPvalue, 0) })
 kendall <- cor.test(mat[,1],mat[,2], method="kendall")
 kendallPvalue <- c(kendallPvalue, kendall$p.value)
 kendallTau <- c(kendallTau, kendall$estimate["tau"])
 pearson <- cor.test(mat[,1],mat[,2], method="pearson")
 pearsonPvalue <- c(pearsonPvalue, pearson$p.value)
 pearsonCor <- c(pearsonCor, pearson$estimate["cor"])
}
outFrame <- data.frame(issueName,mannWhitneyPvalue,shapiroWilkPvalue,kendallPvalue,kendallTau,pearsonPvalue,pearsonCor)
write.csv(outFrame, file="correlation-mas-hl.csv", fileEncoding="UTF-8", row.names=FALSE)