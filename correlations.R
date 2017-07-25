# Runs Mann-Whitney test and Kendall Correlation test between architecture-issues and code-issues
# Saves results in "correlations.csv".
#
# To run script from command line:
# "C:\Program Files\R\R-3.3.3\bin\x64\Rscript.exe" myscript.R

data <- read.csv("architecture-and-sonar-issues.csv", header=T)
mat <- matrix(c(data$arch.class.cyclic.occurrences, data$code_smells.long_method),nrow=length(data$arch.class.cyclic.occurrences))
smell <- c()
mannWhitneyP <- c()
kendallP <- c()
kendallTau <- c()
for(i in 4:dim(data)[2]) {
 smell <- c(smell, colnames(data)[i])
 mat <- matrix(c(data$arch.class.cyclic.occurrences, data[,i]),nrow=length(data$arch.class.cyclic.occurrences))
 pvalue <- wilcox.test(mat[,1],mat[,2])$p.value
 mannWhitneyP <- c(mannWhitneyP, pvalue)
 pval <- cor.test(mat[,1],mat[,2], method="kendall")$p.value
 kendallP <- c(kendallP, pval)
 tau <- (cor.test(mat[,1],mat[,2], method="kendall")$estimate["tau"])
 kendallTau <- c(kendallTau, tau)
}
outFrame <- data.frame(smell,mannWhitneyP,kendallP,kendallTau)
write.csv(outFrame, file="correlations.csv", fileEncoding="UTF-8")