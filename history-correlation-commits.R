# Runs Mann-Whitney test and Kendall, Pearson, Spearman correlations between commits and code-issues
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

data <- read.csv("fault-file-commit-grouped.csv", header=T)
measureName <- c()
mannWhitneyPvalue <- c()
shapiroWilkPvalue <- c()
kendallPvalue <- c()
kendallTau <- c()
pearsonPvalue <- c()
pearsonCor <- c()
spearmanPvalue <- c()
spearmanRho <- c()
for(i in 5:dim(data)[2]) {
 measureName <- c(measureName, colnames(data)[i])
 mat <- matrix(c(data$fault.related.commits, data[,i]),nrow=length(data$fault.related.commits))
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
 spearman <- cor.test(mat[,1],mat[,2], method="spearman")
 spearmanPvalue <- c(spearmanPvalue, spearman$p.value)
 spearmanRho <- c(spearmanRho, spearman$estimate["rho"])
}
outFrame <- data.frame(measureName,mannWhitneyPvalue,shapiroWilkPvalue,kendallPvalue,kendallTau,pearsonPvalue,pearsonCor,spearmanPvalue,spearmanRho)
write.csv(outFrame, file="correlation-commits.csv", fileEncoding="UTF-8", row.names=FALSE)