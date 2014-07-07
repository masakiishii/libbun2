int main(void) {
	int i = 0, c = 0;
	while(i < 10) {
		i++;
		if(i % 2 == 0) continue;
		c++;
	}
	
	return 0;
}
