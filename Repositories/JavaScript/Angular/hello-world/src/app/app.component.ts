import { Component } from '@angular/core';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent {
  title = 'hello-world';

  ngAfterViewInit() {
    console.log('컴포넌트 초기화 완료');
  }
}
